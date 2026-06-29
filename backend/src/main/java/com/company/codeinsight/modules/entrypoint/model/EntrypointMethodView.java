package com.company.codeinsight.modules.entrypoint.model;

import lombok.Data;

/**
 * 知识入口复核视图中的方法 DTO
 * <p>对应 ci_entrypoint.methods_json 数组中的单个元素；只用于前端只读展示。</p>
 */
@Data
public class EntrypointMethodView {

    /** 方法名（不含类名） */
    private String methodName;

    /** 方法签名：className#methodName(ParamType1,ParamType2) */
    private String methodSignature;

    /** 方法级触发注解简称（GetMapping / Scheduled / "class-level: @Scheduled" 等） */
    private String annotation;

    /** 仅控制器方法：HTTP 路径 */
    private String httpPath;

    /** 仅控制器方法：HTTP 方法（GET/POST/PUT/DELETE/PATCH） */
    private String httpMethod;
}