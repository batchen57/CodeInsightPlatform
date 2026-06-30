package com.company.codeinsight.common.cluster;

import com.company.codeinsight.modules.quotacontrol.service.AiConcurrencyService;
import com.company.codeinsight.modules.quotacontrol.service.SystemConfigService;
import com.company.codeinsight.modules.task.service.TaskConcurrencyLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 订阅 {@link ConfigRefreshPublisher#CHANNEL}，在任意节点修改 ci_system_config 后同步刷新本机缓存与信号量。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "code-insight.cluster.enabled", havingValue = "true")
public class ConfigRefreshListener implements MessageListener {

    private final RedisMessageListenerContainer listenerContainer;
    private final SystemConfigService systemConfigService;
    private final TaskConcurrencyLimiter taskConcurrencyLimiter;
    private final AiConcurrencyService aiConcurrencyService;

    @PostConstruct
    public void subscribe() {
        listenerContainer.addMessageListener(this, new ChannelTopic(ConfigRefreshPublisher.CHANNEL));
        log.info("ConfigRefreshListener subscribed to {}", ConfigRefreshPublisher.CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = new String(message.getBody());
        log.info("收到配置刷新广播: {}", key);
        systemConfigService.refreshCache();
        if ("*".equals(key) || "task.concurrency".equals(key)) {
            taskConcurrencyLimiter.rebuildGlobal(systemConfigService.getInt("task.concurrency", 2));
        }
        if ("*".equals(key) || "ai.concurrency".equals(key)) {
            aiConcurrencyService.rebuild(systemConfigService.getInt("ai.concurrency", 4));
        }
    }
}
