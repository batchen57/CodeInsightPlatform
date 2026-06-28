package com.company.codeinsight.modules.task.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务流水线中单个阶段的统计摘要。
 * 由 {@link TaskLogSummaryDto#getPipeline()} 承载，前端"执行日志"卡片渲染 Timeline 时使用。
 */
@Data
public class PipelineStageStatDto {

    /** 阶段枚举键：PULLING_CODE / PARSING_CODE / SPLITTING_TASK / AI_ANALYZING / MODULE_HIERARCHY / GENERATING_DOC / MODULE_HIERARCHY_REVIEW */
    private String key;

    /** 阶段中文展示名（与前端 statusMeta 对齐） */
    private String label;

    /** 阶段状态：pending=待开始 / running=进行中 / done=已完成 / skipped=跳过 / error=异常 */
    private String status;

    /** 本阶段耗时（毫秒），未知则为 0 */
    private Long durationMs;

    /** 阶段开始时间（从 pipeline.log 时间戳提取） */
    private LocalDateTime startedAt;

    /** 阶段结束时间 */
    private LocalDateTime endedAt;
}