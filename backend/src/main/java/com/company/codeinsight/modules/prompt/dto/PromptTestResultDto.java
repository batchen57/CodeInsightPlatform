package com.company.codeinsight.modules.prompt.dto;

import lombok.Data;

@Data
public class PromptTestResultDto {
    private int inputTokens;
    private int outputTokens;
    private long durationMs;
    private String result;
    private String errorReason;
}
