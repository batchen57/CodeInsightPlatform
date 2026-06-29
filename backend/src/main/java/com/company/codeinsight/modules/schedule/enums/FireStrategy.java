package com.company.codeinsight.modules.schedule.enums;

/**
 * 定时任务触发策略（与 ci_task.type 对齐）。
 *
 * <ul>
 *   <li>{@link #INCREMENTAL}：默认。增量扫描，按 Git diff 选择性重跑变更文件</li>
 *   <li>{@link #INITIAL}：全量重跑，重新解析整个仓库</li>
 * </ul>
 */
public enum FireStrategy {
    INCREMENTAL,
    INITIAL
}