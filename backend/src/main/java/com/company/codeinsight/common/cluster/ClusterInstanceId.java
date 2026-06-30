package com.company.codeinsight.common.cluster;

import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.UUID;

/**
 * 当前 JVM 实例在集群中的唯一标识（写入 ci_task.claimed_by、分布式锁 value 等）。
 */
@Component
public class ClusterInstanceId {

    private final String id;

    public ClusterInstanceId() {
        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // use default
        }
        String pid = ManagementFactory.getRuntimeMXBean().getName();
        this.id = host + ":" + pid + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String get() {
        return id;
    }
}
