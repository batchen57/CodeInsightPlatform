package com.company.codeinsight.modules.schedule.dto;

import lombok.Data;

/**
 * 更新定时任务的入参（id 由路径给出）。
 * 字段语义同 {@link ScheduleTaskCreateDto}。
 */
@Data
public class ScheduleTaskUpdateDto {
    private String name;
    private String description;
    private String cronExpression;
    private String timezone;
    private Boolean enabled;
    private String fireStrategy;
    private String overlapStrategy;
    private Long modularizePromptId;
    private Long documentPromptId;
    private String modelName;
    private com.company.codeinsight.modules.entrypoint.model.EntryPointConfig entryScanConfig;
    private Boolean requireHierarchyReview;
    private Boolean requireEntrypointReview;
}