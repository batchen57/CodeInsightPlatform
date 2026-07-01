package com.company.codeinsight.common.storage;

/**
 * 存储模式：控制草稿/工作区/发布产物的文件系统路径计算方式。
 * <ul>
 *   <li>{@code LOCAL}  — 单机开发/测试，文件落在当前工作目录</li>
 *   <li>{@code SHARED} — 集群部署，文件落在 NAS/NFS 共享卷</li>
 * </ul>
 */
public enum StorageMode {
    LOCAL,
    SHARED
}
