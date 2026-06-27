package com.company.codeinsight.modules.ai.service;

import com.company.codeinsight.modules.chunk.entity.CodeChunk;
import lombok.Data;

import java.util.List;

/**
 * 大模型归纳与总结核心服务接口
 * 负责调度 AI 大模型处理代码切片功能分析，并将切片分析聚合生成正式的 Markdown 知识草稿。
 */
public interface AiSummaryService {

    /**
     * 对单个代码切片（Chunk）进行 AI 分析归纳
     *
     * @param taskId        关联的任务 ID
     * @param chunkId       代码切片 ID
     * @param promptContent 渲染后的提示词模版正文
     * @param modelName     所使用的 AI 模型唯一标识
     * @return 返回 AI 归纳出的 Markdown 内容段落
     */
    String summarizeChunk(Long taskId, Long chunkId, String promptContent, String modelName);

    /**
     * 聚合分片归纳分析结果并生成业务模块级别的正式 Markdown 知识草稿，写入 ci_knowledge_draft
     *
     * @param taskId        关联的任务 ID
     * @param chunks        任务产生的所有切片列表
     * @param promptContent 提示词模版正文
     */
    void generateDraftDocument(Long taskId, List<CodeChunk> chunks, String promptContent);

    /**
     * 用任意已组装好的 prompt 字符串直接调用大模型
     * 复用 summarizeChunk 的脱敏 / Token 流控 / HTTP / Mock 降级 / 模型热插拔基础设施，
     * 但不再读取 chunk 行范围，prompt 整体由调用方组装（如 analyze_prompt.md 渲染结果）。
     *
     * @param taskId      关联任务 ID（用于 Token 流控和审计）
     * @param promptInput 已渲染的完整 prompt
     * @param modelName   所用模型标识
     * @param callMeta    调用元数据（callStage / classPath），用于审计
     * @return AI 响应文本；调用失败或 Mock 时返回 "{}"
     */
    String summarizeWithPrompt(Long taskId, String promptInput, String modelName, AiCallMeta callMeta);

    /**
     * 调用元数据
     */
    @Data
    class AiCallMeta {
        /** 调用阶段标签，如 "MODULE_HIERARCHY" / "CHUNK_SUMMARY" */
        private String callStage;
        /** 当前分析对象标识（如入口类全限定名） */
        private String classPath;
    }
}

