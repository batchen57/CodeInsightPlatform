package com.company.codeinsight.modules.hierarchy.service;

import com.company.codeinsight.modules.hierarchy.model.ModuleHierarchy;

import java.io.File;

/**
 * 模块层级服务接口
 * 串联入口识别 → AI 提炼 → DTO 内存聚合 → 持久化到 ci_module_hierarchy。
 */
public interface ModuleHierarchyService {

    /**
     * 构建并持久化任务的模块层级
     * 流程：
     *   1) 从数据库加载该任务已有节点，重建 ModuleHierarchy DTO
     *   2) 调用 EntryPointDiscoveryService 识别入口
     *   3) 对每个入口：调 AI 提炼 → 解析 JSON → 合并到 DTO（复用/新增 ID）
     *   4) 把入口 className 注入到对应 function 的 classPaths
     *   5) deleteByTaskId + 全量 insert（保证幂等）
     *
     * @param taskId     关联任务 ID
     * @param projectDir 拉取的本地项目根目录（用于 collectReachableSource 读取源码）
     * @return 构建后的 ModuleHierarchy DTO
     */
    ModuleHierarchy buildAndPersist(Long taskId, File projectDir);

    /**
     * 从数据库加载该任务的模块层级 DTO
     */
    ModuleHierarchy loadByTaskId(Long taskId);
}