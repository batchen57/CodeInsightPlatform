package com.company.codeinsight.modules.token.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.modules.token.entity.TokenUsageAudit;
import java.util.Map;

/**
 * Token 用量审计管理服务接口
 * 负责定义 Token 快捷记账、审计分页明细查询、大屏图表聚合分析、熔断阻断超限校验等业务规则。
 */
public interface TokenAuditService {

    /**
     * 实时记账审计记录一次 Token 消耗，计算估算金额，写入 ci_token_usage_audit 表
     *
     * @param systemId     关联系统 ID
     * @param taskId       关联任务 ID
     * @param modelName    模型名称
     * @param inputTokens  输入 Token
     * @param outputTokens 输出 Token
     * @param type         任务类别类型
     * @param isSuccess    调用是否执行成功
     */
    void logTokenUsage(Long systemId, Long taskId, String modelName, int inputTokens, int outputTokens, String type, boolean isSuccess);

    /**
     * 分页、条件查询明细记录列表
     */
    Page<TokenUsageAudit> listAuditPage(int current, int size, Long systemId, String modelName, String type);

    /**
     * 获取 Token 总体趋势及聚合图表大屏展示数据
     * 包含：今天总消耗，系统排行，模型占比，近期趋势
     *
     * @param systemId 选填的系统 ID，若传入则过滤系统数据，若不传则统计全平台数据
     * @return 复合统计元数据 Map 格式
     */
    Map<String, Object> getAuditStats(Long systemId);

    /**
     * 获取指定分析任务当前已经累计消耗的总 Token 数，用于防失控熔断
     *
     * @param taskId 任务 ID
     * @return 累计消耗的 Token 总数
     */
    int getTaskCumulativeTokens(Long taskId);

    /**
     * 获取指定业务系统在当前自然月内已累计消耗的总 Token 数，用于月度配额限额熔断
     *
     * @param systemId 系统 ID
     * @return 当前月份消耗的 Token 总数
     */
    int getSystemMonthlyTokens(Long systemId);
}

