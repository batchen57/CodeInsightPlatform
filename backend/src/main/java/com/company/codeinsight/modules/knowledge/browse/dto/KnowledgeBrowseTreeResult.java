package com.company.codeinsight.modules.knowledge.browse.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识查看树形模式响应：基准任务元数据 + 模块层级树。
 */
@Data
public class KnowledgeBrowseTreeResult {

    private Long systemId;
    private String systemName;
    private Long repositoryId;
    private String repositoryName;

    /** 实际用于组树的基准任务 ID（自动或手动指定） */
    private Long taskId;

    /** 是否自动解析的基准任务 */
    private Boolean taskAutoResolved;

    /** 文档生成粒度：function / module */
    private String documentGranularity;

    private List<KnowledgeBrowseTreeNode> nodes = new ArrayList<>();
}
