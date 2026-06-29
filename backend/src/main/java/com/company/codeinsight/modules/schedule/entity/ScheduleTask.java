package com.company.codeinsight.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 定时任务配置实体（对应 ci_schedule_task 表）。
 * <p>每次 cron tick 会基于该配置创建一条 ci_task 记录，并写入 ci_schedule_fire_record。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_schedule_task")
public class ScheduleTask extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属业务系统 ID */
    private Long systemId;

    /** 关联的 Git 代码库 ID */
    private Long repositoryId;

    /** 配置名 */
    private String name;

    /** 配置描述 */
    private String description;

    /** cron 表达式（Spring 6 位格式：秒 分 时 日 月 周） */
    private String cronExpression;

    /** 时区 */
    private String timezone;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;

    /** 触发策略：INCREMENTAL / INITIAL */
    private String fireStrategy;

    /** 冲突策略：SKIP / QUEUE / PARALLEL */
    private String overlapStrategy;

    /** 模块提取提示词 ID */
    @TableField("modularize_prompt_id")
    private Long modularizePromptId;

    /** 文档生成提示词 ID */
    @TableField("document_prompt_id")
    private Long documentPromptId;

    /** AI 模型名 */
    private String modelName;

    /** 入口扫描配置 JSON */
    @TableField("entry_scan_config")
    private String entryScanConfig;

    /** 是否启用模块层级调试断点 */
    @TableField("require_hierarchy_review")
    private Integer requireHierarchyReview;

    /** 最近一次触发时间 */
    @TableField("last_fired_at")
    private LocalDateTime lastFiredAt;

    /** 最近一次触发产生的 ci_task.id */
    @TableField("last_task_id")
    private Long lastTaskId;

    /** 最近一次触发状态 */
    @TableField("last_status")
    private String lastStatus;

    /** 下一次触发时间（计算字段） */
    @TableField("next_fire_at")
    private LocalDateTime nextFireAt;

    @TableField("total_fired")
    private Integer totalFired;

    @TableField("total_success")
    private Integer totalSuccess;

    @TableField("total_failed")
    private Integer totalFailed;

    @TableField("total_skipped")
    private Integer totalSkipped;

    @TableField("created_by")
    private Long createdBy;

    /** 软删除时间（null 表示未删除） */
    @TableLogic(value = "null", delval = "now()")
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}