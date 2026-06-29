package com.company.codeinsight.modules.hierarchy.model;

import com.company.codeinsight.common.util.jackson.YnBooleanDeserializer;
import com.company.codeinsight.common.util.jackson.YnBooleanSerializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
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

    /**
     * 人工逐项复核确认标记：JSON 中以 "Y" / "N" 字符串形式呈现
     * <p>前端 JSON tab 中可直接看到哪些模块已被用户复核过</p>
     */
    @JsonSerialize(using = YnBooleanSerializer.class)
    @JsonDeserialize(using = YnBooleanDeserializer.class)
    private Boolean confirmed;

    /** 子模块列表（按 sub_module_id 索引） */
    private Map<String, SubModuleDto> subModules = new LinkedHashMap<>();
}