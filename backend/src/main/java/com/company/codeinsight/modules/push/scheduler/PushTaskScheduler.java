package com.company.codeinsight.modules.push.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.codeinsight.modules.push.service.PushService;

import lombok.extern.slf4j.Slf4j;

/**
 * 推送任务调度器
 *
 * 定时从 Redis 队列中取出推送任务并执行。
 * 通过 {@code code-insight.push.queue.enabled} 控制开关，默认启用。
 * 通过 {@code code-insight.push.scheduler.poll-interval-ms} 控制轮询间隔，默认 2000ms。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "code-insight.push.queue.enabled", havingValue = "true", matchIfMissing = true)
public class PushTaskScheduler {

    @Autowired
    private PushService pushService;

    /**
     * 定时轮询 Redis 队列，取出并执行推送任务。
     * 每次只处理一个任务，避免单次执行时间过长。
     */
    @Scheduled(fixedDelayString = "${code-insight.push.scheduler.poll-interval-ms:2000}")
    public void poll() {
        try {
            pushService.processNextTask();
        } catch (Exception e) {
            log.error("推送任务调度器执行异常", e);
        }
    }
}
