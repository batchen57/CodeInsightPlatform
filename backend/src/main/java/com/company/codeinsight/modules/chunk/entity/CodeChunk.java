package com.company.codeinsight.modules.chunk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ci_chunk")
public class CodeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String filePath;

    private String className;

    private String methodName;

    private String chunkType; // FILE, CLASS, METHOD, DIFF

    private String contentHash;

    private Integer startLine;

    private Integer endLine;

    private Integer tokenEstimate;

    private String status; // PENDING, ANALYZED, FAILED

    private String errorReason;

    private LocalDateTime createdAt;
}
