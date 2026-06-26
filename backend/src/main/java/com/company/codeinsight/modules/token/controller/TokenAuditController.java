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

@Tag(name = "Token审计", description = "Token 审计与统计分析接口")
@RestController
@RequestMapping("/token-audit")
public class TokenAuditController {

    @Autowired
    private TokenAuditService tokenAuditService;

    @Operation(summary = "Token 使用聚合统计")
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(@RequestParam(required = false) Long systemId) {
        Map<String, Object> stats = tokenAuditService.getAuditStats(systemId);
        return ApiResponse.success(stats);
    }

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
