package com.company.codeinsight.modules.schedule.scheduler;

import com.company.codeinsight.modules.schedule.entity.ScheduleTask;
import com.company.codeinsight.modules.schedule.service.ScheduleTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时任务调度器入口。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>每 {@code code-insight.schedule.poll-interval-ms} 毫秒拉取一次 enabled=1 且 next_fire_at &lt;= now 的调度配置</li>
 *   <li>对每条配置调用 {@link ScheduleTaskService#tryFire(Long)}，由 Service 内部加分布式锁 + 路由 SKIP/QUEUE/PARALLEL</li>
 *   <li>另起一轮：消费 QUEUE 策略排队的 schedule_id（任一 schedule 的 last_task 不再运行时出队并 fireNow）</li>
 * </ul>
 *
 * <p>参考已有 {@code PushTaskScheduler} 实现，可通过
 * {@code code-insight.schedule.enabled=false} 关闭。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "code-insight.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduleExecutor {

    @Autowired
    private ScheduleTaskService scheduleTaskService;

    /**
     * 主循环：扫描到期的调度并触发。
     */
    @Scheduled(fixedDelayString = "${code-insight.schedule.poll-interval-ms:60000}")
    public void poll() {
        try {
            List<ScheduleTask> due = scheduleTaskService.findDueSchedules();
            if (due == null || due.isEmpty()) {
                return;
            }
            log.debug("调度轮询：到期 schedule 数={}", due.size());
            for (ScheduleTask s : due) {
                try {
                    scheduleTaskService.tryFire(s.getId());
                } catch (Exception ex) {
                    // 单条失败不影响其他调度
                    log.error("调度触发失败 scheduleId={}", s.getId(), ex);
                }
            }
        } catch (Exception ex) {
            log.error("调度轮询异常", ex);
        }
    }

    /**
     * 队列消费：QUEUE 策略下被排队等待的 schedule_id，
     * 检查上一次任务是否已完成，是则出队触发。
     */
    @Scheduled(fixedDelayString = "${code-insight.schedule.queue-poll-interval-ms:30000}")
    public void drainQueue() {
        try {
            Long scheduleId;
            while ((scheduleId = scheduleTaskService.popQueuedScheduleId()) != null) {
                try {
                    scheduleTaskService.tryFire(scheduleId);
                } catch (Exception ex) {
                    log.error("队列消费失败 scheduleId={}", scheduleId, ex);
                }
            }
        } catch (Exception ex) {
            log.error("队列轮询异常", ex);
        }
    }
}