package com.company.codeinsight.modules.model.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI 模型按天聚合的调用趋势点。
 */
@Data
public class AiModelMetricTrendPoint {

    private String date;

    private Long calls;

    private Long tokens;

    private BigDecimal cost;
}
