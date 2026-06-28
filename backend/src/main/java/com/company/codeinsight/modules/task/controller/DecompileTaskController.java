package com.company.codeinsight.modules.task.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.draft.dto.SaveDraftRequest;
import com.company.codeinsight.modules.draft.service.DraftService;
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
    private DraftService draftService;

    @Autowired
    private OperationLogService operationLogService;

    /**
     * 创建全量解析分析任务（扫描整个分支所有代码文件并重新进行 AI 归纳）
     */
    @Operation(summary = "创建全量初始化任务")
    @PostMapping("/initial")
    public ApiResponse<DecompileTask> createInitial(@RequestBody TaskCreateRequest request) {
        DecompileTask task = decompileTaskService.createInitialTask(request.getSystemId(), request.getRepositoryId(), request.getPromptVersion(), request.getModelName());
        operationLogService.logOperation(request.getSystemId(), task.getId(), "CREATE_TASK", "创建全量初始化反编译任务", null, true);
        return ApiResponse.success(task);
    }

    /**
     * 创建增量解析分析任务（自动根据 Git Commit 差异，只对有变化的代码切片执行更新与 AI 分析）
     */
    @Operation(summary = "创建增量分析任务")
    @PostMapping("/incremental")
    public ApiResponse<DecompileTask> createIncremental(@RequestBody TaskCreateRequest request) {
        DecompileTask task = decompileTaskService.createIncrementalTask(request.getSystemId(), request.getRepositoryId(), request.getPromptVersion(), request.getModelName());
        operationLogService.logOperation(request.getSystemId(), task.getId(), "CREATE_TASK", "创建增量分析反编译任务", null, true);
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
            @RequestParam(required = false) String type,
            @RequestParam(name = "statuses", required = false) java.util.List<String> statuses) {
        Page<DecompileTask> page = decompileTaskService.listTasksPage(current, size, systemId, status, type, statuses);
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
     * 任务级「确认通过」：把任务下整组草稿一次性置为 CONFIRMED，
     * 工作区晋升 COMPLETED，任务推进到 CONFIRMED。
     *
     * <p>这是复核工作区「确认通过」按钮的真实语义入口 — 操作粒度是任务，不是单个文件。
     * 细粒度的单文件确认仍可使用 {@code POST /drafts/{id}/confirm}，但不会再触发级联状态升级。</p>
     *
     * @param id      任务 ID
     * @param author  操作人（默认 Admin）
     * @param comment 可选的任务级通过意见（可空）
     */
    @Operation(summary = "任务级「确认通过」")
    @PostMapping("/{id}/confirm")
    public ApiResponse<Void> confirmTask(
            @PathVariable Long id,
            @RequestBody SaveDraftRequest body) {
        String author = body.getAuthor() != null ? body.getAuthor() : "Admin";
        String comment = body.getComment();
        draftService.confirmTask(id, author, comment);
        operationLogService.logOperation(
                null, id, "CONFIRM_TASK",
                "任务级确认通过整组草稿" + (comment != null && !comment.isBlank() ? "，意见：" + comment : ""),
                null, true
        );
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
     * 任务中心顶部 chips 角标：按状态分组统计各分组的任务数量。
     * 返回 key 固定为 ALL / RUNNING / PENDING_REVIEW / CONFIRMED / CLOSED。
     *
     * @param systemId 可选系统过滤
     */
    @Operation(summary = "任务状态分组统计（用于顶部 chips 角标）")
    @GetMapping("/summary")
    public ApiResponse<java.util.Map<String, Long>> getTaskSummary(
            @RequestParam(required = false) Long systemId) {
        return ApiResponse.success(decompileTaskService.countByStatusGroup(systemId));
    }

    /**
     * 创建任务参数封装对象
     */
    @Data
    public static class TaskCreateRequest {
        private Long systemId;
        private Long repositoryId;
        private Integer promptVersion;
        private String modelName;
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

