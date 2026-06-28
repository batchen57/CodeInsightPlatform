package com.company.codeinsight.modules.hierarchy.model;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 功能级 DTO
 * 与 analyze_prompt.md 输出的 function 节点对应：
 * {"id": "fXXXX", "function_name": "...", "class_paths": ["..."], "method_signatures": ["..."]}
 */
@Data
public class FunctionDto {

    /** 5位 Base62 ID（f 前缀），由 Base62Generator 生成 */
    private String id;

    /** 功能名（业务语义，如"白名单查询"） */
    private String functionName;

    /**
     * 关联的入口类全限定名集合
     * AI 在模块提取阶段也会输出（与 method_signatures 一同），程序侧仍兜底注入入口类（兼容旧数据）
     */
    private Set<String> classPaths = new LinkedHashSet<>();

    /**
     * 该功能涉及的方法签名列表
     * 由 AI 在模块提取阶段直接输出，格式为 "methodName(ParamType1, ParamType2)"（不含返回类型）
     * 例：["listUsers(Integer, Integer)", "createUser(UserDTO)", "deleteUser(Long)"]
     * 用于阶段 2 按方法签名粒度反查调用链，喂 AI 文档生成
     */
    private Set<String> methodSignatures = new LinkedHashSet<>();
}