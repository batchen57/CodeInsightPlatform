package com.company.codeinsight.modules.schedule.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.schedule.dto.ScheduleTaskCreateDto;
import com.company.codeinsight.modules.schedule.dto.ScheduleTaskPageQuery;
import com.company.codeinsight.modules.schedule.dto.ScheduleTaskUpdateDto;
import com.company.codeinsight.modules.schedule.entity.ScheduleFireRecord;
import com.company.codeinsight.modules.schedule.entity.ScheduleTask;
import com.company.codeinsight.modules.schedule.service.ScheduleTaskService;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.service.DecompileTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 定时任务调度配置 REST 控制器。
 *
 * <p>提供配置 CRUD、启用/禁用、立即触发、触发历史分页查询等接口。
 * 实际调度由 {@code ScheduleExecutor} 周期轮询触发，Controller 仅暴露管理面能力。</p>
 */
@Tag(name = "定时任务管理", description = "定时任务调度的配置 CRUD 与触发历史接口")
@RestController
@RequestMapping("/schedules")
@Validated
public class ScheduleTaskController {

    @Autowired
    private ScheduleTaskService scheduleTaskService;

    @Autowired
    private DecompileTaskService decompileTaskService;

    /**
     * 分页查询调度配置
     */
    @Operation(summary = "调度配置分页查询")
    @GetMapping
    public ApiResponse<PageResult<ScheduleTask>> page(@ModelAttribute ScheduleTaskPageQuery query) {
        Page<ScheduleTask> p = scheduleTaskService.pageQuery(query);
        return ApiResponse.success(new PageResult<>(p.getTotal(), p.getSize(), p.getCurrent(), p.getRecords()));
    }

    /**
     * 调度配置详情
     */
    @Operation(summary = "调度配置详情")
    @GetMapping("/{id}")
    public ApiResponse<ScheduleTask> detail(@PathVariable Long id) {
        return ApiResponse.success(scheduleTaskService.getById(id));
    }

    /**
     * 新增调度配置
     */
    @Operation(summary = "新增调度配置")
    @PostMapping
    public ApiResponse<ScheduleTask> create(@RequestBody ScheduleTaskCreateDto dto) {
        return ApiResponse.success(scheduleTaskService.createConfig(dto));
    }

    /**
     * 更新调度配置
     */
    @Operation(summary = "更新调度配置")
    @PutMapping("/{id}")
    public ApiResponse<ScheduleTask> update(@PathVariable Long id, @RequestBody ScheduleTaskUpdateDto dto) {
        return ApiResponse.success(scheduleTaskService.updateConfig(id, dto));
    }

    /**
     * 删除（软删）调度配置
     */
    @Operation(summary = "删除调度配置")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scheduleTaskService.softDelete(id);
        return ApiResponse.success();
    }

    /**
     * 启用调度
     */
    @Operation(summary = "启用调度")
    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        scheduleTaskService.enable(id);
        return ApiResponse.success();
    }

    /**
     * 禁用调度
     */
    @Operation(summary = "禁用调度")
    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        scheduleTaskService.disable(id);
        return ApiResponse.success();
    }

    /**
     * 立即触发一次（不依赖 cron，立即创建 ci_task 并启动）
     */
    @Operation(summary = "立即触发")
    @PostMapping("/{id}/trigger")
    public ApiResponse<Map<String, Object>> triggerNow(@PathVariable Long id) {
        ScheduleFireRecord rec = scheduleTaskService.triggerNow(id);
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("scheduleId", id);
        body.put("taskId", rec == null ? null : rec.getTaskId());
        body.put("fireRecordId", rec == null ? null : rec.getId());
        body.put("status", rec == null ? null : rec.getStatus());
        return ApiResponse.success(body);
    }

    /**
     * 触发历史分页
     */
    @Operation(summary = "触发历史")
    @GetMapping("/{id}/fire-records")
    public ApiResponse<PageResult<ScheduleFireRecord>> fireRecords(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size) {
        Page<ScheduleFireRecord> p = scheduleTaskService.listFireRecords(id, current, size);
        return ApiResponse.success(new PageResult<>(p.getTotal(), p.getSize(), p.getCurrent(), p.getRecords()));
    }

    /**
     * 该定时任务已触发的所有反编译任务（分页，按 created_at DESC）。
     * <p>复用 DecompileTaskService 的 listTasksPage 并强制 scheduleId 过滤；状态/类型 chip 仍可用。
     * 复用 /tasks 的同一套分组与字段，避免在 schedule 模块重复一套任务 DTO。</p>
     */
    @Operation(summary = "定时任务已触发的反编译任务")
    @GetMapping("/{id}/tasks")
    public ApiResponse<PageResult<DecompileTask>> triggeredTasks(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(name = "statuses", required = false) List<String> statuses) {
        Page<DecompileTask> p = decompileTaskService.listTasksPage(
                current, size, null, status, type, statuses, id, "SCHEDULED");
        return ApiResponse.success(new PageResult<>(p.getTotal(), p.getSize(), p.getCurrent(), p.getRecords()));
    }
}