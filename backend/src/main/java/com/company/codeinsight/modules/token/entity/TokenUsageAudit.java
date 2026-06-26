package com.company.codeinsight.modules.token.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ci_token_usage_audit")
public class TokenUsageAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long systemId;

    private Long taskId;

    private Long userId;

    private Integer promptVersion;

    private String modelName;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private BigDecimal cost;

    private String type; // INITIAL, INCREMENTAL, TEST

    private Integer status; // 0-失败, 1-成功

    private LocalDateTime createdAt;
}
