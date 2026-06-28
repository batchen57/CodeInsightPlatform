package com.company.codeinsight.modules.model.dto;

import lombok.Data;

/**
 * AI 模型连接测试结果。
 */
@Data
public class AiModelTestResult {

    private Boolean success;

    private Long durationMs;

    private String message;

    private String responseSummary;
}
