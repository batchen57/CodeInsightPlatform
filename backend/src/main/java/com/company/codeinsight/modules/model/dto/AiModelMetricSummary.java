package com.company.codeinsight.modules.model.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI 模型累计调用指标。
 */
@Data
public class AiModelMetricSummary {

    private String modelName;

    private Long totalCalls;

    private Long totalTokens;

    private BigDecimal totalCost;
}
