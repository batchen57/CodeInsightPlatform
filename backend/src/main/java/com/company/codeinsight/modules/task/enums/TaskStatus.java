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
     * 知识入口人工复核断点（介于 SPLITTING_TASK 与 AI_ANALYZING 之间）。
     * 流水线在切片完成后、调用 AI 提取模块层级之前，把识别到的入口类与方法落表 ci_entrypoint，
     * 等待用户在页面上确认（继续）或驳回（终止任务）。
     */
    ENTRYPOINT_REVIEW,
    /**
     * AI 归纳分析中（多线程调度大模型 API 进行切片功能提取）
     */
    AI_ANALYZING,
    /**
     * AI 模块层级提炼中（从每个入口提交 AI 提炼并维护 module_hierarchy DTO）
     */
    MODULE_HIERARCHY,
    /**
     * 模块层级人工复核断点（AI 提炼完成后等待用户在页面上编辑 module_hierarchy，确认后继续生成文档）
     */
    MODULE_HIERARCHY_REVIEW,
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

