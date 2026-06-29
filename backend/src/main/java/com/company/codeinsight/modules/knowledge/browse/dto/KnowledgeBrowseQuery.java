package com.company.codeinsight.modules.knowledge.browse.dto;

import lombok.Data;

/**
 * 知识查看列表查询入参。
 * <p>所有字段除 systemId 外都可为空；为空表示不施加该维度过滤。</p>
 */
@Data
public class KnowledgeBrowseQuery {

    /** 必填：按系统聚合 */
    private Long systemId;

    /** 文件类型过滤：DRAFT / INDEX / MANIFEST / ALL（默认 ALL） */
    private String type;

    /** 简单搜索关键字：按文件名 LIKE '%keyword%'（不区分大小写） */
    private String keyword;

    /** 可选：限定具体任务 ID */
    private Long taskId;

    /** 可选：限定具体知识版本 ID（用于按版本过滤） */
    private Long versionId;

    /** 可选：草稿状态过滤（DRAFT / EDITING / CONFIRMED / PUSHED / ARCHIVED），仅 type=DRAFT 时生效 */
    private String status;

    /** 可选：updatedAt 下界（ISO 字符串） */
    private String createdAtStart;

    /** 可选：updatedAt 上界（ISO 字符串） */
    private String createdAtEnd;
}