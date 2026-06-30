package com.company.codeinsight.common.cluster;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 配置变更后通知集群内其他节点刷新本地缓存。
 */
@Component
@RequiredArgsConstructor
public class ConfigRefreshPublisher {

    public static final String CHANNEL = "ci:config:refresh";

    private final StringRedisTemplate redisTemplate;

    public void publish(String configKey) {
        redisTemplate.convertAndSend(CHANNEL, configKey == null ? "*" : configKey);
    }
}
