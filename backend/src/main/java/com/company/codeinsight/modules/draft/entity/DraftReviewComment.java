package com.company.codeinsight.modules.draft.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 草稿评审复核意见实体类
 * 对应数据库中的 ci_draft_review_comment 表，保存评审人员在驳回或复核草稿时所填写的备注及批注意见。
 */
@Data
@TableName("ci_draft_review_comment")
public class DraftReviewComment {

    /**
     * 自增主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的草稿 ID
     */
    private Long draftId;

    /**
     * 填写评审意见的人员用户名
     */
    private String author;

    /**
     * 评审反馈的具体意见内容
     */
    private String comment;

    /**
     * 评审记录创建时间
     */
    private LocalDateTime createdAt;
}

