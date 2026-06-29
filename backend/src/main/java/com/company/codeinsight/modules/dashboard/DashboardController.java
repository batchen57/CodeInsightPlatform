package com.company.codeinsight.modules.dashboard;

import com.company.codeinsight.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘 / 看板 统计接口
 * <p>URL 前缀 {@code /api/dashboard}：</p>
 * <ul>
 *   <li>{@code GET /tasks/stats} — 任务概览聚合</li>
 *   <li>{@code GET /ai-usage/stats} — AI 模型用量统计</li>
 *   <li>{@code GET /pipeline/stats} — 流水线各阶段分析</li>
 *   <li>{@code GET /coverage} — 系统覆盖报表</li>
 * </ul>
 */
@Tag(name = "仪表盘统计", description = "仪表盘 / 看板页面的聚合统计数据接口")
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Operation(summary = "任务概览聚合统计")
    @GetMapping("/tasks/stats")
    public ApiResponse<Map<String, Object>> getTaskStats(
            @RequestParam(required = false, defaultValue = "30") int days) {
        return ApiResponse.success(dashboardService.getTaskStats(days));
    }

    @Operation(summary = "AI 模型用量统计")
    @GetMapping("/ai-usage/stats")
    public ApiResponse<Map<String, Object>> getAiUsageStats(
            @RequestParam(required = false) Long systemId) {
        return ApiResponse.success(dashboardService.getAiUsageStats(systemId));
    }

    @Operation(summary = "流水线各阶段耗时与失败率分析")
    @GetMapping("/pipeline/stats")
    public ApiResponse<List<Map<String, Object>>> getPipelineStats() {
        return ApiResponse.success(dashboardService.getPipelineStageStats());
    }

    @Operation(summary = "系统覆盖报表")
    @GetMapping("/coverage")
    public ApiResponse<List<Map<String, Object>>> getSystemCoverage() {
        return ApiResponse.success(dashboardService.getSystemCoverage());
    }
}