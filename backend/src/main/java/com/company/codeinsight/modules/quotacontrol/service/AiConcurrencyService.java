package com.company.codeinsight.modules.quotacontrol.service;

import com.company.codeinsight.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

/**
 * AI 调用并发控制器。
 * <p>
 * 容量由 {@code ai.concurrency} 配置（ci_system_config）控制，启动时初始化。
 * 任何一次 AI 同步调用前 {@link #tryAcquire()} 拿许可，结束后 {@link #release()} 释放。
 * 拿不到许可时抛 {@link BusinessException}。
 * </p>
 */
@Slf4j
@Service
public class AiConcurrencyService {

    @Autowired
    private SystemConfigService systemConfigService;

    private volatile Semaphore semaphore;

    @PostConstruct
    public void init() {
        int n = systemConfigService.getInt("ai.concurrency", 4);
        rebuild(n);
    }

    /**
     * 重建信号量（配置变更后调用）。
     */
    public synchronized void rebuild(int permits) {
        int n = Math.max(1, permits);
        this.semaphore = new Semaphore(n, true);
        log.info("AI 并发信号量已重建: permits={}", n);
    }

    /**
     * 非阻塞获取许可；拿不到立即抛错。
     */
    public void tryAcquire() {
        if (semaphore == null) {
            rebuild(systemConfigService.getInt("ai.concurrency", 4));
        }
        if (!semaphore.tryAcquire()) {
            throw new BusinessException("AI 调用并发已达上限，请稍后重试");
        }
    }

    public void release() {
        if (semaphore != null) {
            semaphore.release();
        }
    }

    public int availablePermits() {
        return semaphore == null ? 0 : semaphore.availablePermits();
    }
}
