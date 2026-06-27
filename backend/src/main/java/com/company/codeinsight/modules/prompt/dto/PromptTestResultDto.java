package com.company.codeinsight.modules.prompt.dto;

import lombok.Data;

/**
 * 提示词模板试跑测试返回结果数据传输对象（DTO）
 * 用于返回试跑时的 Token 统计、网络耗时、AI 总结内容及异常报错原因。
 */
@Data
public class PromptTestResultDto {
    /**
     * 测试调用消耗的输入 Token 数
     */
    private int inputTokens;
    /**
     * 测试调用产生返回的输出 Token 数
     */
    private int outputTokens;
    /**
     * 接口调用的实际网络耗时（毫秒）
     */
    private long durationMs;
    /**
     * AI 返回的归纳分析 Markdown 内容
     */
    private String result;
    /**
     * 大模型调用发生失败时的报错原因
     */
    private String errorReason;
}

