package com.company.codeinsight.modules.draft.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模块知识草稿实体类
 * 对应数据库中的 ci_knowledge_draft 表，记录从代码分片（Chunk）总结聚合而成的模块级 Markdown 文档草稿的修订状态与存储路径。
 */
@Data
@TableName("ci_knowledge_draft")
public class KnowledgeDraft {

    /**
     * 自增主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属的评审工作区 ID
     */
    private Long workspaceId;

    /**
     * 草稿 Markdown 相对文档路径
     */
    private String filePath;

    /**
     * 业务知识子模块的唯一分组名称
     */
    private String moduleName;

    /**
     * 正文 Markdown 内容文件在磁盘上的物理 URI 路径
     */
    private String contentUri;

    /**
     * 草稿流转状态：AI_GENERATED-AI生成, PENDING_REVIEW-待复核, REVIEWING-复核中, REVISED-已修改, CONFIRMED-确认通过, REJECTED-已驳回, PUSHED-已推送, ARCHIVED-已归档
     */
    private String status;

    /**
     * 文档内容的哈希校验和（MD5），用于增量检测与版本比较
     */
    private String hash;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

