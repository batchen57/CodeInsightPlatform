package com.company.codeinsight.modules.hierarchy.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子模块级 DTO
 * 与 analyze_prompt.md 输出的 sub_module 节点对应
 */
@Data
public class SubModuleDto {

    /** 5位 Base62 ID（s 前缀） */
    private String id;

    /** 子模块名（具体业务功能名） */
    private String subModuleName;

    /** 关键词列表 */
    private List<String> keywords = new ArrayList<>();

    /** 功能列表（按 function_id 索引） */
    private Map<String, FunctionDto> functions = new LinkedHashMap<>();
}