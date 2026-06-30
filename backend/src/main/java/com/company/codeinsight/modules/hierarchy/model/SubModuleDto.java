package com.company.codeinsight.modules.hierarchy.model;

import com.company.codeinsight.common.util.jackson.YnBooleanJson;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

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

    /** 功能列表（按 function_id 索引） */
    private Map<String, FunctionDto> functions = new LinkedHashMap<>();
}
