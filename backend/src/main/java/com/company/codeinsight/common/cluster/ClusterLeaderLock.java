package com.company.codeinsight.common.cluster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis SET NX 的简易 Leader 选举：同一 lockKey 仅一个实例持有。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterLeaderLock {

    private final StringRedisTemplate redisTemplate;
    private final ClusterInstanceId instanceId;
    private final ClusterProperties clusterProperties;

    public boolean tryAcquireLeader(String lockKey) {
        String holder = instanceId.get();
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                holder,
                Duration.ofSeconds(clusterProperties.getLeaderLockTtlSeconds()));
        if (Boolean.TRUE.equals(ok)) {
            return true;
        }
        String current = redisTemplate.opsForValue().get(lockKey);
        if (holder.equals(current)) {
            redisTemplate.expire(lockKey, clusterProperties.getLeaderLockTtlSeconds(), TimeUnit.SECONDS);
            return true;
        }
        return false;
    }

    public void releaseLeader(String lockKey) {
        String holder = instanceId.get();
        String current = redisTemplate.opsForValue().get(lockKey);
        if (holder.equals(current)) {
            redisTemplate.delete(lockKey);
        }
    }
}
