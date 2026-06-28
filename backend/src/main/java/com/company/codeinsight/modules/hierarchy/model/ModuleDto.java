package com.company.codeinsight.modules.hierarchy.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块级 DTO
 * 与 analyze_prompt.md 输出的 module 节点对应
 */
@Data
public class ModuleDto {

    /** 5位 Base62 ID（m 前缀） */
    private String id;

    /** 模块名（业务领域/场景，向最大公约数抽象） */
    private String moduleName;

    /** 关键词列表（只使用名词，3-5个） */
    private List<String> keywords = new ArrayList<>();

    /** 子模块列表（按 sub_module_id 索引） */
    private Map<String, SubModuleDto> subModules = new LinkedHashMap<>();
}