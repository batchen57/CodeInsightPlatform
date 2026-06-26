package com.company.codeinsight.modules.ai.service;

import com.company.codeinsight.modules.chunk.entity.CodeChunk;
import java.util.List;

public interface AiSummaryService {

    /**
     * 对单个切片进行 AI 分析归纳
     *
     * @return 返回 AI 归纳的 Markdown 片段
     */
    String summarizeChunk(Long taskId, Long chunkId, String promptContent, String modelName);

    /**
     * 合并切片总结并生成模块级的正式 Markdown 草稿，写入 ci_knowledge_draft
     */
    void generateDraftDocument(Long taskId, List<CodeChunk> chunks, String promptContent);
}
