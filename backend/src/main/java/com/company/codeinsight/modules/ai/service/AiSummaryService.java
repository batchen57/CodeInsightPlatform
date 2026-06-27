package com.company.codeinsight.modules.ai.service;

import com.company.codeinsight.modules.chunk.entity.CodeChunk;
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
}

