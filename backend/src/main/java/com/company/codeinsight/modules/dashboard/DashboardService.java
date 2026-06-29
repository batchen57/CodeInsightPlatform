package com.company.codeinsight.modules.dashboard;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘 / 看板 统计服务接口
 */
public interface DashboardService {

    /** 任务概览：按状态 / 类型 / 系统 聚合 + 最近 N 天趋势 */
    Map<String, Object> getTaskStats(int days);

    /** AI 模型用量统计：按模型 / 阶段分组 Token + 调用次数 + 成本 + 成功率 */
    Map<String, Object> getAiUsageStats(Long systemId);

    /** 流水线各阶段耗时与失败率分析 */
    List<Map<String, Object>> getPipelineStageStats();

    /** 系统覆盖报表：每个系统的任务数 / 草稿数 / 推送版本数 / 最近反编译时间 */
    List<Map<String, Object>> getSystemCoverage();
}