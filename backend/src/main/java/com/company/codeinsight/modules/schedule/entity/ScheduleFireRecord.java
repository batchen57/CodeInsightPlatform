package com.company.codeinsight.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务每次触发的记录（对应 ci_schedule_fire_record 表）。
 * 每次 cron tick（无论实际创建任务还是被跳过）都会写一行。
 */
@Data
@TableName("ci_schedule_fire_record")
public class ScheduleFireRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调度配置 ID */
    @TableField("schedule_id")
    private Long scheduleId;

    /** 本次触发创建的知识构建任务 ID（SKIPPED 时为空） */
    @TableField("task_id")
    private Long taskId;

    /** 实际触发时间 */
    @TableField("fire_time")
    private LocalDateTime fireTime;

    /** 计划触发时间（与 cron 计算对齐） */
    @TableField("planned_time")
    private LocalDateTime plannedTime;

    /** CREATED / RUNNING / SUCCESS / FAILED / SKIPPED / QUEUED */
    private String status;

    /** 跳过原因（仅 SKIPPED 时填写） */
    @TableField("skip_reason")
    private String skipReason;

    /** 错误信息 */
    @TableField("error_message")
    private String errorMessage;

    /** 耗时（毫秒），由后台异步任务收尾时填写 */
    @TableField("duration_ms")
    private Long durationMs;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}