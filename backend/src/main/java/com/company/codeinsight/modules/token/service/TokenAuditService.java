package com.company.codeinsight.modules.token.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.modules.token.entity.TokenUsageAudit;
import java.util.Map;

public interface TokenAuditService {

    /**
     * 审计记录 Token 消耗
     */
    void logTokenUsage(Long systemId, Long taskId, String modelName, int inputTokens, int outputTokens, String type, boolean isSuccess);

    /**
     * 分页查询明细
     */
    Page<TokenUsageAudit> listAuditPage(int current, int size, Long systemId, String modelName, String type);

    /**
     * 获取 Token 总体趋势及聚合图表数据
     * 包含：今天总消耗，系统排行，模型占比，近期趋势
     */
    Map<String, Object> getAuditStats(Long systemId);

    /**
     * 获取当前任务累计消耗的 Token 数
     */
    int getTaskCumulativeTokens(Long taskId);

    /**
     * 获取系统本月累计消耗的 Token 数
     */
    int getSystemMonthlyTokens(Long systemId);
}
