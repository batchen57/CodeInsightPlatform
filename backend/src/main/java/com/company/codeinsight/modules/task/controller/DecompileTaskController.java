package com.company.codeinsight.modules.task.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.log.service.OperationLogService;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.service.DecompileTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "反编译任务管理", description = "代码拉取、静态解析与AI分析任务的启停及监控接口")
@RestController
@RequestMapping("/tasks")
@Validated
public class DecompileTaskController {

    @Autowired
    private DecompileTaskService decompileTaskService;

    @Autowired
    private OperationLogService operationLogService;

    @Operation(summary = "创建全量初始化任务")
    @PostMapping("/initial")
    public ApiResponse<DecompileTask> createInitial(@RequestBody TaskCreateRequest request) {
        DecompileTask task = decompileTaskService.createInitialTask(request.getSystemId(), request.getRepositoryId(), request.getPromptVersion(), request.getModelName());
        operationLogService.logOperation(request.getSystemId(), task.getId(), "CREATE_TASK", "创建全量初始化反编译任务", null, true);
        return ApiResponse.success(task);
    }

    @Operation(summary = "创建增量分析任务")
    @PostMapping("/incremental")
    public ApiResponse<DecompileTask> createIncremental(@RequestBody TaskCreateRequest request) {
        DecompileTask task = decompileTaskService.createIncrementalTask(request.getSystemId(), request.getRepositoryId(), request.getPromptVersion(), request.getModelName());
        operationLogService.logOperation(request.getSystemId(), task.getId(), "CREATE_TASK", "创建增量分析反编译任务", null, true);
        return ApiResponse.success(task);
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public ApiResponse<DecompileTask> getTask(@PathVariable Long id) {
        DecompileTask task = decompileTaskService.getById(id);
        return ApiResponse.success(task);
    }

    @Operation(summary = "任务列表查询")
    @GetMapping
    public ApiResponse<PageResult<DecompileTask>> listTasks(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long systemId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        Page<DecompileTask> page = decompileTaskService.listTasksPage(current, size, systemId, status, type);
        PageResult<DecompileTask> result = new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(), page.getRecords());
        return ApiResponse.success(result);
    }

    @Operation(summary = "启动任务")
    @PostMapping("/{id}/start")
    public ApiResponse<Void> startTask(@PathVariable Long id) {
        decompileTaskService.startTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "终止任务")
    @PostMapping("/{id}/terminate")
    public ApiResponse<Void> terminateTask(@PathVariable Long id) {
        decompileTaskService.terminateTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "重试任务")
    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retryTask(@PathVariable Long id) {
        decompileTaskService.retryTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "查询任务进度")
    @GetMapping("/{id}/progress")
    public ApiResponse<TaskProgressDto> getProgress(@PathVariable Long id) {
        DecompileTask task = decompileTaskService.getById(id);
        if (task == null) {
            return ApiResponse.error("任务不存在");
        }
        TaskProgressDto dto = new TaskProgressDto();
        dto.setStatus(task.getStatus());
        dto.setProgress(task.getProgress());
        dto.setErrorReason(task.getErrorReason());
        return ApiResponse.success(dto);
    }

    @Data
    public static class TaskCreateRequest {
        private Long systemId;
        private Long repositoryId;
        private Integer promptVersion;
        private String modelName;
    }

    @Data
    public static class TaskProgressDto {
        private String status;
        private Integer progress;
        private String errorReason;
    }
}
