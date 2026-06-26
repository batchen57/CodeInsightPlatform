package com.company.codeinsight.modules.log.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.log.entity.OperationLog;
import com.company.codeinsight.modules.log.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "操作日志", description = "系统及反编译操作审计日志接口")
@RestController
@RequestMapping("/logs")
public class LogQueryController {

    @Autowired
    private OperationLogService operationLogService;

    @Operation(summary = "日志分页查询")
    @GetMapping
    public ApiResponse<PageResult<OperationLog>> listLogs(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long systemId,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Integer isSuccess) {
        Page<OperationLog> page = operationLogService.listLogsPage(current, size, systemId, taskId, username, actionType, isSuccess);
        PageResult<OperationLog> result = new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(), page.getRecords());
        return ApiResponse.success(result);
    }

    @Operation(summary = "日志详情查询")
    @GetMapping("/{id}")
    public ApiResponse<OperationLog> getLogDetail(@PathVariable Long id) {
        OperationLog log = operationLogService.getById(id);
        return ApiResponse.success(log);
    }
}
