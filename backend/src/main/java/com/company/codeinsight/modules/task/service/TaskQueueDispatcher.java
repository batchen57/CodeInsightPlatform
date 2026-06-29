package com.company.codeinsight.modules.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.enums.TaskStatus;
import com.company.codeinsight.modules.task.mapper.DecompileTaskMapper;
import com.company.codeinsight.modules.task.service.impl.TaskStateMachineServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 反编译任务队列调度器：
 *  - 定期（默认 5s）拉 PENDING 任务
 *  - 按 priority DESC, created_at ASC 排序
 *  - 尝试获取 TaskConcurrencyLimiter 的全局 + 系统许可
 *  - 拿到 → 状态机转 PULLING_CODE → 触发 pipeline
 *  - 拿不到 → 跳过，下个 tick 再试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskQueueDispatcher {

    private final DecompileTaskMapper taskMapper;
    private final DecompileTaskService decompileTaskService;
    private final TaskStateMachineServiceImpl stateMachineService;
    private final TaskConcurrencyLimiter limiter;

    /**
     * 调度间隔（默认 5s）。可通过 ci_system_config 'task.concurrency' 调全局上限；
     * 调度频率通过 application.yml 'code-insight.task.queue-dispatch-interval-ms' 调整。
     */
    @Scheduled(fixedDelayString = "${code-insight.task.queue-dispatch-interval-ms:5000}")
    public void dispatch() {
        int permits = limiter.globalAvailablePermits();
        if (permits <= 0) {
            return; // 全部占满，无需扫库
        }
        // 拉最多 (permits * 2) 条 PENDING，limit 数保证不会拉太多导致长 GC 停顿
        int limit = Math.max(permits * 2, 4);
        List<DecompileTask> pending = taskMapper.selectList(
                new LambdaQueryWrapper<DecompileTask>()
                        .eq(DecompileTask::getStatus, TaskStatus.PENDING.name())
                        .orderByDesc(DecompileTask::getPriority)
                        .orderByAsc(DecompileTask::getCreatedAt)
                        .last("LIMIT " + limit)
        );
        if (pending.isEmpty()) return;

        for (DecompileTask t : pending) {
            if (!limiter.tryAcquire(t.getSystemId())) {
                continue; // 本轮拿不到许可，下个 tick 再试
            }
            try {
                // 状态机 PENDING → PULLING_CODE
                stateMachineService.transitTo(t, TaskStatus.PULLING_CODE, null);
                // 触发 pipeline（runPipeline 已在 finally 释放 limiter）
                decompileTaskService.runPipeline(t.getId());
            } catch (Exception e) {
                limiter.release(t.getSystemId());
                log.error("dispatcher 触发任务 #{} 失败", t.getId(), e);
            }
        }
    }
}
