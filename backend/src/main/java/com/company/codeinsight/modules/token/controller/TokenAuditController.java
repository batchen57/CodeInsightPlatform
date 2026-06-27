package com.company.codeinsight.modules.token.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.token.entity.TokenUsageAudit;
import com.company.codeinsight.modules.token.service.TokenAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Token 用量审计分析控制器
 * 提供按业务系统维度的 Token 汇总指标统计，以及详细 Token 扣费记录分页列表的 REST API 端点。
 */
@Tag(name = "Token审计", description = "Token 审计与统计分析接口")
@RestController
@RequestMapping("/token-audit")
public class TokenAuditController {

    @Autowired
    private TokenAuditService tokenAuditService;

    /**
     * 获取 Token 总体开销聚合统计数据（包括总计、月度限额、可用额度、各模型占比图表信息）
     *
     * @param systemId 系统 ID
     */
    @Operation(summary = "Token 使用聚合统计")
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(@RequestParam(required = false) Long systemId) {
        Map<String, Object> stats = tokenAuditService.getAuditStats(systemId);
        return ApiResponse.success(stats);
    }

    /**
     * 分页查询单次大模型 API 调用的 Token 审计消费详情列表
     */
    @Operation(summary = "Token 审计明细分页")
    @GetMapping("/page")
    public ApiResponse<PageResult<TokenUsageAudit>> getPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long systemId,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) String type) {
        Page<TokenUsageAudit> page = tokenAuditService.listAuditPage(current, size, systemId, modelName, type);
        PageResult<TokenUsageAudit> result = new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(), page.getRecords());
        return ApiResponse.success(result);
    }
}

