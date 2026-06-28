package com.company.codeinsight.modules.hierarchy.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模块层级聚合 DTO（顶层）
 * 一个任务对应一个 ModuleHierarchy 实例，内存中维护完整的模块/子模块/功能树。
 */
@Data
public class ModuleHierarchy {

    /** 关联任务 ID */
    private Long taskId;

    /** 关联系统 ID */
    private Long systemId;

    /** 模块映射（按 module_id 索引，保留插入顺序便于阅读） */
    private Map<String, ModuleDto> modules = new LinkedHashMap<>();
}