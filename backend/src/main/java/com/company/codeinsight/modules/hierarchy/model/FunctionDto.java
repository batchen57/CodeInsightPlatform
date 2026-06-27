package com.company.codeinsight.modules.hierarchy.model;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 功能级 DTO
 * 与 analyze_prompt.md 输出的 function 节点对应：
 * {"id": "fXXXX", "function_name": "...", "class_paths": ["..."]}
 */
@Data
public class FunctionDto {

    /** 5位 Base62 ID（f 前缀），由 Base62Generator 生成 */
    private String id;

    /** 功能名（业务语义，如"白名单查询"） */
    private String functionName;

    /** 关联的入口类全限定名集合（程序侧注入，AI 不输出） */
    private Set<String> classPaths = new LinkedHashSet<>();
}