package com.company.codeinsight.modules.task.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务执行日志摘要 DTO。
 * 单次接口返回，供前端"执行日志"卡片 + "查看完整日志"模态框顶栏使用。
 *
 * 数据来源（详见 TaskLogSummaryServiceImpl#summarize）：
 *   - ci_task                  → 顶层 status / progress / durationMs / startedAt / endedAt / modelName
 *   - ci_chunk                 → counters.totalChunks / chunksByType / chunksAnalyzed / chunksFailed / chunksPending
 *   - ci_ai_call_record        → aiCalls.total / success / failed
 *   - ci_file_snapshot         → counters.totalFiles
 *   - ci_module_hierarchy      → current.moduleTotal
 *   - pipeline.log             → pipeline[] 阶段列表与各阶段 durationMs、current.chunkIndex、lastError
 *   - 配置 code-insight.ai.mock → aiMock
 */
@Data
public class TaskLogSummaryDto {

    private Long taskId;
    private String status;
    private Integer progress;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String modelName;

    /** 是否启用本地 Mock；来自 code-insight.ai.mock 配置 */
    private Boolean aiMock;

    /** 各阶段的耗时与状态，按状态机推进顺序排列 */
    private List<PipelineStageStatDto> pipeline;

    /** 切片/文件级计数 */
    private Counters counters;

    /** AI 调用成功/失败计数 */
    private AiCalls aiCalls;

    /** 当前正在处理的进度（-1 表示未知） */
    private Current current;

    /** 失败原因的单行摘要（不带堆栈），用于"执行日志"卡片友好提示 */
    private String lastError;

    @Data
    public static class Counters {
        private Integer totalFiles;
        private Integer totalChunks;
        /** chunk_type → count；FILE / CLASS / METHOD / DIFF */
        private Map<String, Integer> chunksByType;
        private Integer chunksAnalyzed;
        private Integer chunksFailed;
        private Integer chunksPending;
    }

    @Data
    public static class AiCalls {
        private Integer total;
        private Integer success;
        private Integer failed;
    }

    @Data
    public static class Current {
        /** -1 表示未知 */
        private Integer fileIndex;
        private Integer totalFiles;
        private Integer chunkIndex;
        private Integer totalChunks;
        private Integer moduleIndex;
        private Integer moduleTotal;
    }
}