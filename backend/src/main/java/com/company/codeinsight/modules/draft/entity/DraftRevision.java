package com.company.codeinsight.modules.draft.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ci_draft_revision")
public class DraftRevision {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long draftId;

    private String contentUri;

    private String author;

    private String remark;

    private LocalDateTime createdAt;
}
