package com.company.codeinsight.common.cluster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis Set 的分布式并发计数（成员为 holder token，如 task:123 / ai:uuid）。
 * <p>进程崩溃时成员可能残留；{@link #release} 与 {@link #reconcileStale} 负责清理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedPermits {

    private static final String KEY_PREFIX = "ci:permits:";

    private final StringRedisTemplate redisTemplate;

    public boolean tryAcquire(String pool, String holder, int maxPermits) {
        if (maxPermits <= 0) {
            return false;
        }
        String key = KEY_PREFIX + pool;
        Long size = redisTemplate.opsForSet().size(key);
        long current = size == null ? 0 : size;
        if (current >= maxPermits) {
            return false;
        }
        Long added = redisTemplate.opsForSet().add(key, holder);
        if (added == null || added == 0L) {
            // 已持有，幂等
            return true;
        }
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
        return true;
    }

    public void release(String pool, String holder) {
        redisTemplate.opsForSet().remove(KEY_PREFIX + pool, holder);
    }

    public long count(String pool) {
        Long size = redisTemplate.opsForSet().size(KEY_PREFIX + pool);
        return size == null ? 0 : size;
    }

    /** 移除 Set 中指定 holder（任务终态后清孤儿许可） */
    public void reconcileStale(String pool, Set<String> staleHolders) {
        if (staleHolders == null || staleHolders.isEmpty()) {
            return;
        }
        String key = KEY_PREFIX + pool;
        for (String h : staleHolders) {
            redisTemplate.opsForSet().remove(key, h);
        }
        log.debug("reconcile permits pool={} removed={}", pool, staleHolders.size());
    }
}
