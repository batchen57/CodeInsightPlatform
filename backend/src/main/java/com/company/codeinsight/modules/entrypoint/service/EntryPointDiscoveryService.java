package com.company.codeinsight.modules.entrypoint.service;

import com.company.codeinsight.modules.entrypoint.model.EntryPoint;
import com.company.codeinsight.modules.entrypoint.model.EntryPointConfig;

import java.io.File;
import java.util.List;

/**
 * 反编译项目入口识别服务
 * 通过注解识别（主）+ 调用链反查（辅）双路并联，发现项目中的业务入口类。
 * 同时支持任务级 EntryPointConfig 自定义入口识别与排除规则。
 */
public interface EntryPointDiscoveryService {

    /** 兼容旧调用：config=null 走默认 Controller/JOB/MQ 行为 */
    List<EntryPoint> discoverEntries(Long taskId, File projectDir);

    /** 新调用：传自定义 EntryPointConfig */
    List<EntryPoint> discoverEntries(Long taskId, File projectDir, EntryPointConfig config);

    /** 兼容旧调用 */
    String collectReachableSource(Long taskId, String entryClassName, File projectDir);

    /**
     * 新调用：带配置，BFS 时排除命中排除规则的依赖类。
     * 入口类自身若命中排除规则 → 返回空字符串。
     */
    String collectReachableSource(Long taskId, String entryClassName, File projectDir, EntryPointConfig config);
}