package com.company.codeinsight.modules.callchain.service;

import java.util.Set;

/**
 * 方法调用链图服务接口
 * 阶段 2 文档生成专用：从入口方法签名出发，BFS 反查所有可达方法签名集合。
 */
public interface MethodCallGraphService {

    /**
     * 按入口方法签名集合 BFS 出所有可达方法签名（含入口自身）
     *
     * @param taskId          任务 ID
     * @param rootSignatures  入口方法签名（格式 "className#methodName(ParamType1, ParamType2)"）
     * @return                可达方法签名集合（已去重）
     */
    Set<String> resolveReachableMethods(Long taskId, Set<String> rootSignatures);
}