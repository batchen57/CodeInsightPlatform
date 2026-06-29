package com.company.codeinsight.modules.schedule.enums;

/**
 * 定时任务冲突策略：当 cron 触发时上一次任务尚未结束的处置方式。
 *
 * <ul>
 *   <li>{@link #SKIP}：默认。直接跳过本次触发，写 fire_record.status=SKIPPED</li>
 *   <li>{@link #QUEUE}：排队等上一次结束再触发；通过 Redis 列表暂存 schedule_id</li>
 *   <li>{@link #PARALLEL}：允许并发，每次 cron tick 都创建一条新任务</li>
 * </ul>
 */
public enum OverlapStrategy {
    SKIP,
    QUEUE,
    PARALLEL
}