package com.company.codeinsight.modules.quotacontrol.service;

import com.company.codeinsight.common.cluster.ClusterProperties;
import com.company.codeinsight.common.cluster.RedisDistributedPermits;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.quotacontrol.service.SystemConfigService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * AI 调用并发控制器。
 * <p>集群模式使用 Redis 分布式计数；单机模式使用 JVM Semaphore。</p>
 */
@Slf4j
@Service
public class AiConcurrencyService {

    private static final String POOL_AI = "ai:global";

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private ClusterProperties clusterProperties;

    @Autowired(required = false)
    private RedisDistributedPermits redisPermits;

    private volatile Semaphore localSemaphore;

    /** 当前线程持有的 Redis holder，用于 release */
    private final ThreadLocal<String> redisHolder = new ThreadLocal<>();

    @PostConstruct
    public void init() {
        rebuild(systemConfigService.getInt("ai.concurrency", 4));
    }

    public synchronized void rebuild(int permits) {
        int n = Math.max(1, permits);
        this.localSemaphore = new Semaphore(n, true);
        log.info("AI 并发已重建: permits={}, cluster={}", n, clusterProperties.isEnabled());
    }

    public void tryAcquire() {
        if (clusterProperties.isEnabled() && redisPermits != null) {
            int max = systemConfigService.getInt("ai.concurrency", 4);
            String holder = "ai:" + UUID.randomUUID();
            if (!redisPermits.tryAcquire(POOL_AI, holder, max)) {
                throw new BusinessException("AI 调用并发已达上限，请稍后重试");
            }
            redisHolder.set(holder);
            return;
        }
        if (localSemaphore == null) {
            rebuild(systemConfigService.getInt("ai.concurrency", 4));
        }
        if (!localSemaphore.tryAcquire()) {
            throw new BusinessException("AI 调用并发已达上限，请稍后重试");
        }
    }

    public void release() {
        if (clusterProperties.isEnabled() && redisPermits != null) {
            String holder = redisHolder.get();
            if (holder != null) {
                redisPermits.release(POOL_AI, holder);
                redisHolder.remove();
            }
            return;
        }
        if (localSemaphore != null) {
            localSemaphore.release();
        }
    }

    public int availablePermits() {
        if (clusterProperties.isEnabled() && redisPermits != null) {
            int max = systemConfigService.getInt("ai.concurrency", 4);
            return Math.max(0, max - (int) redisPermits.count(POOL_AI));
        }
        return localSemaphore == null ? 0 : localSemaphore.availablePermits();
    }
}
