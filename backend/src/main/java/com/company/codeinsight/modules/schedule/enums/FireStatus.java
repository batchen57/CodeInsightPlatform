package com.company.codeinsight.modules.schedule.enums;

/**
 * 定时任务每次触发的状态（写入 ci_schedule_fire_record.status）。
 */
public enum FireStatus {
    /** 触发并已成功调用 decompileTaskService.create*Task + startTask */
    CREATED,
    /** 任务已创建并跑起来，但本次触发记录尚未拿到最终结果（中间状态） */
    RUNNING,
    /** 关联的 ci_task 已结束且 status 属于正常终态（PENDING_REVIEW / CONFIRMED / PUSHED） */
    SUCCESS,
    /** 关联的 ci_task 进入 FAILED / CANCELLED */
    FAILED,
    /** 本次因 overlap_strategy=SKIP 被跳过 */
    SKIPPED,
    /** 本次因 overlap_strategy=QUEUE 进入排队，等待下次轮询 */
    QUEUED
}