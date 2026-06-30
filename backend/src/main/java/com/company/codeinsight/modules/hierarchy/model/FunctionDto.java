package com.company.codeinsight.modules.hierarchy.model;

import com.company.codeinsight.common.util.jackson.YnBooleanJson;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

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

    /**
     * 人工逐项复核确认标记：JSON 中以 "Y" / "N" 字符串形式呈现
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Boolean confirmed;

    @JsonIgnore
    public Boolean getConfirmed() {
        return confirmed;
    }

    @JsonIgnore
    public void setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
    }

    @JsonGetter("confirmed")
    public String getConfirmedYn() {
        return YnBooleanJson.format(confirmed);
    }

    @JsonSetter("confirmed")
    public void setConfirmedYn(String raw) {
        this.confirmed = YnBooleanJson.parse(raw);
    }
}
