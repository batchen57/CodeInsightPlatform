package com.company.codeinsight.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ci_knowledge_version")
public class KnowledgeVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long systemId;

    private Long repositoryId;

    private Long taskId;

    private String versionNum;

    private String sourceBranch;

    private String sourceCommit;

    private String targetBranch;

    private String targetCommit;

    private Integer promptVersion;

    private String modelName;

    private String status; // DRAFT, PUSHING, PUSHED, FAILED

    private String confirmedBy;

    private LocalDateTime confirmedAt;

    private LocalDateTime pushedAt;

    private LocalDateTime createdAt;
}
