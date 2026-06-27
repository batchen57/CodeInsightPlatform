package com.company.codeinsight.modules.task.enums;

/**
 * 任务类型枚举
 * 定义分析任务是全量还是增量模式。
 */
public enum TaskType {
    /**
     * 全量初始化任务：清除历史快照并完整构建知识文档
     */
    INITIAL,
    /**
     * 增量更新任务：基于 Git Commit 的代码变动文件执行部分更新与增量分析
     */
    INCREMENTAL
}

