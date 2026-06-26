package com.company.codeinsight.modules.task.enums;

public enum TaskStatus {
    DRAFT,             // 草稿
    PENDING,           // 待执行
    PULLING_CODE,      // 代码拉取中
    PARSING_CODE,      // 代码解析中
    SPLITTING_TASK,    // 任务切片中
    AI_ANALYZING,      // AI分析中
    GENERATING_DOC,    // 知识生成中
    PENDING_REVIEW,    // 待复核
    REVIEWING,         // 复核中
    CONFIRMED,         // 已确认
    PUSHING,           // 推送中
    PUSHED,            // 已推送
    FAILED,            // 执行失败
    CANCELLED,         // 已取消
    ARCHIVED           // 已归档
}
