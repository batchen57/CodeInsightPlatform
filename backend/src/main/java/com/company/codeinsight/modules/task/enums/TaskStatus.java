package com.company.codeinsight.modules.task.enums;

/**
 * 任务状态机核心状态枚举
 * 映射 DecompileTask 任务生命周期中的所有阶段状态。
 */
public enum TaskStatus {
    /**
     * 草稿新建
     */
    DRAFT,
    /**
     * 待执行（入排队队列）
     */
    PENDING,
    /**
     * 代码拉取中（克隆 Git 库或复制本地文件）
     */
    PULLING_CODE,
    /**
     * 代码静态解析中（执行 JavaParser 静态 AST 解析）
     */
    PARSING_CODE,
    /**
     * 任务切片中（按照语法块/物理文件/变更进行分片及 Token 估算）
     */
    SPLITTING_TASK,
    /**
     * AI 归纳分析中（多线程调度大模型 API 进行切片功能提取）
     */
    AI_ANALYZING,
    /**
     * 知识生成中（整合切片 Markdown 并写入 ci_knowledge_draft 进行版本归档）
     */
    GENERATING_DOC,
    /**
     * 待人工复核评审
     */
    PENDING_REVIEW,
    /**
     * 复核人工编辑中
     */
    REVIEWING,
    /**
     * 评审已确认通过
     */
    CONFIRMED,
    /**
     * 知识推送中（将 Markdown 分批写入 Git）
     */
    PUSHING,
    /**
     * 知识已成功推送
     */
    PUSHED,
    /**
     * 任务执行异常失败
     */
    FAILED,
    /**
     * 被用户手动终止/取消
     */
    CANCELLED,
    /**
     * 历史数据已归档
     */
    ARCHIVED
}

