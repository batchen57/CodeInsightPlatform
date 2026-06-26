package com.company.codeinsight.modules.draft.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ci_draft_workspace")
public class DraftWorkspace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long systemId;

    private Long repositoryId;

    private String status; // ACTIVE, COMPLETED, ARCHIVED

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
