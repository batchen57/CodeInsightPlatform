package com.company.codeinsight.modules.draft.service;

import com.company.codeinsight.common.cluster.ClusterInstanceId;
import com.company.codeinsight.common.cluster.ClusterProperties;
import com.company.codeinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 草稿编辑分布式锁（Redis SET NX + TTL，支持续租）。
 */
@Service
@RequiredArgsConstructor
public class DraftEditLockService {

    private static final String KEY_PREFIX = "ci:draft:edit-lock:";

    private final StringRedisTemplate redisTemplate;
    private final ClusterInstanceId instanceId;
    private final ClusterProperties clusterProperties;

    public void acquire(Long draftId, String editor) {
        if (draftId == null) {
            return;
        }
        String key = KEY_PREFIX + draftId;
        String holder = lockValue(editor);
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                key, holder, Duration.ofSeconds(clusterProperties.getDraftEditLockTtlSeconds()));
        if (Boolean.TRUE.equals(ok)) {
            return;
        }
        String current = redisTemplate.opsForValue().get(key);
        if (holder.equals(current)) {
            renew(draftId, editor);
            return;
        }
        throw new BusinessException("该草稿正由其他用户编辑中，请稍后再试");
    }

    public void renew(Long draftId, String editor) {
        if (draftId == null) {
            return;
        }
        String key = KEY_PREFIX + draftId;
        String holder = lockValue(editor);
        String current = redisTemplate.opsForValue().get(key);
        if (!holder.equals(current)) {
            throw new BusinessException("编辑锁已失效或被他人占用");
        }
        redisTemplate.expire(key, clusterProperties.getDraftEditLockTtlSeconds(), TimeUnit.SECONDS);
    }

    public void release(Long draftId, String editor) {
        if (draftId == null) {
            return;
        }
        String key = KEY_PREFIX + draftId;
        String holder = lockValue(editor);
        String current = redisTemplate.opsForValue().get(key);
        if (holder.equals(current)) {
            redisTemplate.delete(key);
        }
    }

    private String lockValue(String editor) {
        String who = editor != null && !editor.isBlank() ? editor.trim() : "anonymous";
        return instanceId.get() + "|" + who;
    }
}
