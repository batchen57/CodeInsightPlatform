package com.company.codeinsight.modules.knowledge.browse.dto;

import lombok.Data;

/**
 * 知识查看树形模式查询入参。
 * <p>systemId + repositoryId 必填；taskId 可选，为空时自动选取该仓库最新已生成文档的任务。</p>
 */
@Data
public class KnowledgeBrowseTreeQuery {

    /** 必填：系统 ID */
    private Long systemId;

    /** 必填：仓库 ID */
    private Long repositoryId;

    /** 可选：指定基准任务；为空则自动解析 */
    private Long taskId;
}
