package com.company.codeinsight.modules.task.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.hierarchy.model.ModuleHierarchy;
import com.company.codeinsight.modules.hierarchy.service.ModuleHierarchyService;
import com.company.codeinsight.modules.log.service.OperationLogService;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.service.DecompileTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 反编译及扫描任务控制器
 * 提供全量初始化分析任务创建、增量分析任务创建、任务列表获取、任务启动/中止/重试及异步进度轮询等 REST 接口。
 */
@Tag(name = "反编译任务管理", description = "代码拉取、静态解析与AI分析任务的启停及监控接口")
@RestController
@RequestMapping("/tasks")
@Validated
public class DecompileTaskController {

    @Autowired
    private DecompileTaskService decompileTaskService;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private ModuleHierarchyService moduleHierarchyService;

    @org.springframework.beans.factory.annotation.Value("${code-insight.storage.local-path:./storage}")
    private String storageBase;

    /**
     * 创建全量解析分析任务（扫描整个分支所有代码文件并重新进行 AI 归纳）
     */
    @Operation(summary = "创建全量初始化任务")
    @PostMapping("/initial")
    public ApiResponse<DecompileTask> createInitial(@RequestBody TaskCreateRequest request) {
        DecompileTask task = decompileTaskService.createInitialTask(request.getSystemId(), request.getRepositoryId(),
                request.getModularizePromptId(), request.getDocumentPromptId(),
                request.getModelName(), request.getEntryScanConfig(), request.getRequireHierarchyReview());
        operationLogService.logOperation(request.getSystemId(), task.getId(), "CREATE_TASK", "创建全量初始化反编译任务" + (Boolean.TRUE.equals(request.getRequireHierarchyReview()) ? "（启用模块层级调试）" : "（跳过模块层级调试）"), null, true);
        return ApiResponse.success(task);
    }

    /**
     * 创建增量解析分析任务（自动根据 Git Commit 差异，只对有变化的代码切片执行更新与 AI 分析）
     */
    @Operation(summary = "创建增量分析任务")
    @PostMapping("/incremental")
    public ApiResponse<DecompileTask> createIncremental(@RequestBody TaskCreateRequest request) {
        DecompileTask task = decompileTaskService.createIncrementalTask(request.getSystemId(), request.getRepositoryId(),
                request.getModularizePromptId(), request.getDocumentPromptId(),
                request.getModelName(), request.getEntryScanConfig(), request.getRequireHierarchyReview());
        operationLogService.logOperation(request.getSystemId(), task.getId(), "CREATE_TASK", "创建增量分析反编译任务" + (Boolean.TRUE.equals(request.getRequireHierarchyReview()) ? "（启用模块层级调试）" : "（跳过模块层级调试）"), null, true);
        return ApiResponse.success(task);
    }

    /**
     * 根据主键查询特定任务最新状态与基本元数据
     */
    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public ApiResponse<DecompileTask> getTask(@PathVariable Long id) {
        DecompileTask task = decompileTaskService.getById(id);
        return ApiResponse.success(task);
    }

    /**
     * 分页多条件查询分析任务列表
     */
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

    /**
     * 触发并启动一个处于待运行的分析任务（触发异步流水线线程）
     */
    @Operation(summary = "启动任务")
    @PostMapping("/{id}/start")
    public ApiResponse<Void> startTask(@PathVariable Long id) {
        decompileTaskService.startTask(id);
        return ApiResponse.success();
    }

    /**
     * 强行终止一个正在进行中或挂起状态的分析任务
     */
    @Operation(summary = "终止任务")
    @PostMapping("/{id}/terminate")
    public ApiResponse<Void> terminateTask(@PathVariable Long id) {
        decompileTaskService.terminateTask(id);
        return ApiResponse.success();
    }

    /**
     * 重试运行一个分析失败的扫描任务
     */
    @Operation(summary = "重试任务")
    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retryTask(@PathVariable Long id) {
        decompileTaskService.retryTask(id);
        return ApiResponse.success();
    }

    /**
     * 便捷前端 2.5s 定时器轮询状态及数字进度条的轻量级 DTO 查询接口
     */
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

    /**
     * 读取任务真实执行日志（pipeline 写入的 pipeline.log 文件）
     */
    @Operation(summary = "读取任务执行日志")
    @GetMapping("/{id}/log")
    public ApiResponse<String> getExecutionLog(@PathVariable Long id) {
        java.io.File logFile = new java.io.File(storageBase, "task_" + id + "/pipeline.log");
        if (!logFile.exists()) {
            return ApiResponse.success("");
        }
        try {
            return ApiResponse.success(java.nio.file.Files.readString(logFile.toPath()));
        } catch (Exception e) {
            return ApiResponse.error("读取日志失败: " + e.getMessage());
        }
    }

    /**
     * 查询任务的模块层级（人工复核断点用）
     * <p>
     * 返回从 ci_module_hierarchy 重建的 ModuleHierarchy DTO（含 classPaths）。
     * 主要用于前端在 MODULE_HIERARCHY_REVIEW 状态下拉取并展示可编辑的模块树。
     */
    @Operation(summary = "查询任务模块层级")
    @GetMapping("/{id}/module-hierarchy")
    public ApiResponse<ModuleHierarchy> getModuleHierarchy(@PathVariable Long id) {
        DecompileTask task = decompileTaskService.getById(id);
        if (task == null) {
            return ApiResponse.error("任务不存在");
        }
        ModuleHierarchy hierarchy = moduleHierarchyService.loadByTaskId(id);
        hierarchy.setTaskId(id);
        hierarchy.setSystemId(task.getSystemId());
        return ApiResponse.success(hierarchy);
    }

    /**
     * 替换任务的模块层级（人工复核断点用）
     * <p>
     * 前端在复核抽屉内编辑完成后整体提交：后端校验 ID/层级结构后 deleteByTaskId + 全量 insert 落表。
     * 不修改 task 自身状态；状态的推进由 resume 接口负责。
     */
    @Operation(summary = "替换任务模块层级")
    @PutMapping("/{id}/module-hierarchy")
    public ApiResponse<ModuleHierarchy> replaceModuleHierarchy(@PathVariable Long id, @RequestBody ModuleHierarchy replacement) {
        DecompileTask task = decompileTaskService.getById(id);
        if (task == null) {
            return ApiResponse.error("任务不存在");
        }
        ModuleHierarchy saved = moduleHierarchyService.replaceHierarchy(id, replacement);
        operationLogService.logOperation(
                task.getSystemId(),
                id,
                "EDIT_MODULE_HIERARCHY",
                "人工复核模块层级: 模块数=" + saved.getModules().size(),
                null,
                true
        );
        return ApiResponse.success(saved);
    }

    /**
     * 模块层级人工复核完成后恢复流水线
     * <p>
     * 仅当任务处于 MODULE_HIERARCHY_REVIEW 时可调用；后续流转 GENERATING_DOC → 生成草稿 → PENDING_REVIEW。
     */
    @Operation(summary = "恢复模块层级复核后流水线")
    @PostMapping("/{id}/module-hierarchy/resume")
    public ApiResponse<Void> resumeModuleHierarchyReview(@PathVariable Long id) {
        decompileTaskService.resumeAfterHierarchyReview(id);
        return ApiResponse.success();
    }

    /**
     * 创建任务参数封装对象
     */
    @Data
    public static class TaskCreateRequest {
        private Long systemId;
        private Long repositoryId;
        /** 模块提取提示词 ID（ci_prompt 主键） */
        private Long modularizePromptId;
        /** 文档生成提示词 ID（ci_prompt 主键） */
        private Long documentPromptId;
        private String modelName;
        /** 入口扫描配置（仅前端策略步骤填写时传入，null 走默认 Controller/JOB/MQ 兜底） */
        private com.company.codeinsight.modules.entrypoint.model.EntryPointConfig entryScanConfig;
        /** 是否启用模块层级调试断点；null 时按默认 TRUE 处理 */
        private Boolean requireHierarchyReview;
    }

    /**
     * 任务进度快速返回传输对象
     */
    @Data
    public static class TaskProgressDto {
        private String status;
        private Integer progress;
        private String errorReason;
    }
}

