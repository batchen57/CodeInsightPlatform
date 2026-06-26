package com.company.codeinsight.modules.draft.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ci_draft_source_reference")
public class DraftSourceReference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long draftId;

    private String filePath;

    private Integer startLine;

    private Integer endLine;

    private LocalDateTime createdAt;
}
