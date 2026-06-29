package com.company.codeinsight.modules.task.service;

import com.company.codeinsight.modules.quotacontrol.service.SystemConfigService;
import com.company.codeinsight.modules.system.entity.SystemApplication;
import com.company.codeinsight.modules.system.mapper.SystemApplicationMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

/**
 * 反编译任务并发闸门：
 *  - 全局 Semaphore：从 ci_system_config 'task.concurrency' 读取（默认 2）
 *  - 每系统 Semaphore：从 ci_system.max_concurrent_tasks 读取（默认 1），懒加载
 *
 * 任务开始时 acquire(task.systemId)，pipeline 终态 finally release(task.systemId)。
 * 任何一边拿不到 → 当前 tick 跳过；下个 tick 重新尝试。
 */
@Slf4j
@Service
public class TaskConcurrencyLimiter {

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private SystemApplicationMapper systemMapper;

    /** 全局 Semaphore，fair=true 避免饥饿 */
    private volatile Semaphore global;

    /** 按 systemId 缓存的 Semaphore，懒加载（容量来自 ci_system.max_concurrent_tasks） */
    private final ConcurrentMap<Long, Semaphore> perSystem = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        rebuildGlobal(systemConfigService.getInt("task.concurrency", 2));
    }

    /** 重建全局 Semaphore（监听 task.concurrency 变更后调用） */
    public synchronized void rebuildGlobal(int permits) {
        int n = Math.max(1, permits);
        this.global = new Semaphore(n, true);
        log.info("TaskConcurrencyLimiter 全局并发已重建: permits={}", n);
    }

    /**
     * 尝试获取指定系统的并发许可（同时拿全局 + 系统）。
     * 任一失败立即归还已拿到的，避免泄漏。
     */
    public boolean tryAcquire(Long systemId) {
        if (global == null) {
            rebuildGlobal(systemConfigService.getInt("task.concurrency", 2));
        }
        Semaphore sys = getOrCreateSystemSemaphore(systemId);
        boolean gotGlobal = global.tryAcquire();
        if (!gotGlobal) return false;
        boolean gotSys = sys.tryAcquire();
        if (!gotSys) {
            global.release();
            return false;
        }
        return true;
    }

    /** 释放指定系统的许可（必须与 tryAcquire 配对调用） */
    public void release(Long systemId) {
        if (systemId != null) {
            Semaphore sys = perSystem.get(systemId);
            if (sys != null) {
                try { sys.release(); } catch (Exception ignored) { /* 超过 permits 时不抛 */ }
            }
        }
        if (global != null) {
            try { global.release(); } catch (Exception ignored) { /* 同上 */ }
        }
    }

    /** 全局当前可用许可数（用于 dispatcher 拉多少 PENDING 任务） */
    public int globalAvailablePermits() {
        return global == null ? 0 : global.availablePermits();
    }

    /** 获取或创建系统级 Semaphore（容量来自 ci_system.max_concurrent_tasks） */
    private Semaphore getOrCreateSystemSemaphore(Long systemId) {
        if (systemId == null) {
            // 兜底：系统 id 缺失时给一个默认大小的 Semaphore
            return perSystem.computeIfAbsent(-1L, k -> new Semaphore(1, true));
        }
        return perSystem.computeIfAbsent(systemId, k -> {
            int permits = 1;
            try {
                SystemApplication sys = systemMapper.selectById(systemId);
                if (sys != null && sys.getMaxConcurrentTasks() != null && sys.getMaxConcurrentTasks() > 0) {
                    permits = sys.getMaxConcurrentTasks();
                }
            } catch (Exception e) {
                log.warn("读取系统 {} 的 max_concurrent_tasks 失败,回退 1", systemId, e);
            }
            log.info("TaskConcurrencyLimiter 系统 {} 初始化并发: permits={}", systemId, permits);
            return new Semaphore(permits, true);
        });
    }
}
