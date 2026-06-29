package com.company.codeinsight.modules.schedule.dto;

import com.company.codeinsight.modules.entrypoint.model.EntryPointConfig;
import lombok.Data;

/**
 * 新增定时任务的入参 DTO。
 */
@Data
public class ScheduleTaskCreateDto {

    /** 系统 ID（必填） */
    private Long systemId;

    /** 代码库 ID（必填） */
    private Long repositoryId;

    /** 配置名（必填） */
    private String name;

    /** 配置描述 */
    private String description;

    /** cron 表达式（必填，Spring 6 位格式） */
    private String cronExpression;

    /** 时区，默认 Asia/Shanghai */
    private String timezone;

    /** 是否启用，默认 true */
    private Boolean enabled;

    /** 触发策略：INCREMENTAL / INITIAL，默认 INCREMENTAL */
    private String fireStrategy;

    /** 冲突策略：SKIP / QUEUE / PARALLEL，默认 SKIP */
    private String overlapStrategy;

    /** 模块提取提示词 ID */
    private Long modularizePromptId;

    /** 文档生成提示词 ID */
    private Long documentPromptId;

    /** AI 模型名（为空时取系统默认模型） */
    private String modelName;

    /** 入口扫描配置 */
    private EntryPointConfig entryScanConfig;

    /** 是否启用模块层级调试断点；null 时按默认 TRUE */
    private Boolean requireHierarchyReview;

    /** 是否启用知识入口复核断点；null 时按默认 TRUE */
    private Boolean requireEntrypointReview;
}