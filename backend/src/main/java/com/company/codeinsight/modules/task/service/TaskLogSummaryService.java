package com.company.codeinsight.modules.task.service;

import com.company.codeinsight.modules.task.dto.TaskLogSummaryDto;

/**
 * 任务执行日志摘要服务。
 * 单次事务性快照聚合 ci_task / ci_chunk / ci_ai_call_record / ci_file_snapshot / ci_module_hierarchy
 * 以及 pipeline.log 文本，供前端"执行日志"卡片与"查看完整日志"模态框共用。
 */
public interface TaskLogSummaryService {

    /**
     * 聚合指定任务的执行摘要。任务不存在时返回仅含 taskId 的空对象（不抛异常，便于前端容错）。
     */
    TaskLogSummaryDto summarize(Long taskId);
}