package com.company.codeinsight.common.cluster;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 集群模式开关与调度参数。
 * <p>单机开发可保持 {@code enabled=false}；多节点部署必须 {@code enabled=true} 且 Redis 可达。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "code-insight.cluster")
public class ClusterProperties {

    /**
     * false：沿用 JVM 内存 Semaphore + 全节点调度（兼容本地开发）。
     * true：Redis 分布式并发 + DB 认领任务 + Leader 调度。
     */
    private boolean enabled = false;

    /** Leader 锁 TTL（秒），持有方需周期性续租 */
    private int leaderLockTtlSeconds = 15;

    /** 任务认领后 lease 时长（小时），用于断点恢复亲和校验 */
    private int taskLeaseHours = 2;

    /** 草稿编辑锁 TTL（秒） */
    private int draftEditLockTtlSeconds = 120;

    /** 草稿编辑锁续期间隔（秒），前端应小于 TTL 周期性续租 */
    private int draftEditLockRenewSeconds = 60;
}
