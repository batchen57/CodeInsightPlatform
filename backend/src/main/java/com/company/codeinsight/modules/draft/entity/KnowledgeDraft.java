package com.company.codeinsight.modules.draft.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ci_knowledge_draft")
public class KnowledgeDraft {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;

    private String filePath;

    private String moduleName;

    private String contentUri;

    private String status; // AI_GENERATED, PENDING_REVIEW, REVIEWING, REVISED, CONFIRMED, REJECTED, PUSHED, ARCHIVED

    private String hash;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
