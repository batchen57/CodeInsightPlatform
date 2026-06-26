package com.company.codeinsight.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ci_ai_call_record")
public class AiCallRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long chunkId;

    private Long promptId;

    private Integer promptVersion;

    private String modelName;

    private Integer inputToken;

    private Integer outputToken;

    private String requestUri;

    private String responseUri;

    private Integer isSuccess; // 0-失败, 1-成功

    private String errorReason;

    private Long durationMs;

    private LocalDateTime createdAt;
}
