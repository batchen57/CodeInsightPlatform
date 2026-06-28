package com.company.codeinsight.modules.prompt.dto;

import lombok.Data;

@Data
public class PromptTestStreamEventDto {

    private String type;

    private String content;

    private int inputTokens;

    private int outputTokens;

    private long durationMs;

    private String errorReason;

    public static PromptTestStreamEventDto content(String content) {
        PromptTestStreamEventDto event = new PromptTestStreamEventDto();
        event.setType("content");
        event.setContent(content);
        return event;
    }

    public static PromptTestStreamEventDto done(int inputTokens, int outputTokens, long durationMs) {
        PromptTestStreamEventDto event = new PromptTestStreamEventDto();
        event.setType("done");
        event.setInputTokens(inputTokens);
        event.setOutputTokens(outputTokens);
        event.setDurationMs(durationMs);
        return event;
    }

    public static PromptTestStreamEventDto error(String errorReason, int inputTokens, long durationMs) {
        PromptTestStreamEventDto event = new PromptTestStreamEventDto();
        event.setType("error");
        event.setErrorReason(errorReason);
        event.setInputTokens(inputTokens);
        event.setDurationMs(durationMs);
        return event;
    }
}
