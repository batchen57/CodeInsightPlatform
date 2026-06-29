package com.company.codeinsight.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.log.service.OperationLogService;
import com.company.codeinsight.modules.system.entity.SystemApplication;
import com.company.codeinsight.modules.system.service.SystemApplicationService;
import com.company.codeinsight.modules.system.vo.SystemSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 业务系统应用管理控制器
 * 提供接入应用系统的新增登记、信息编辑、详情获取、分页模糊条件筛选、状态机切换以及软删除端点。
 */
@Tag(name = "系统管理", description = "系统的增删改查及状态切换接口")
@RestController
@RequestMapping("/systems")
@Validated
public class SystemApplicationController {

    @Autowired
    private SystemApplicationService systemApplicationService;

    @Autowired
    private OperationLogService operationLogService;

    @Operation(summary = "新增系统（向导 Step 1：基本信息）")
    @PostMapping
    public ApiResponse<SystemApplication> createSystem(@Valid @RequestBody SystemApplication system) {
        SystemApplication created = systemApplicationService.createSystemDraft(system);
        operationLogService.logOperation(created.getId(), null, "CREATE_SYSTEM", "创建系统: " + created.getName(), null, true);
        return ApiResponse.success(created);
    }

    @Operation(summary = "编辑系统（基本信息 / 提示词绑定）")
    @PutMapping("/{id}")
    public ApiResponse<SystemApplication> updateSystem(@PathVariable Long id, @Valid @RequestBody SystemApplication system) {
        system.setId(id);
        systemApplicationService.updateById(system);
        operationLogService.logOperation(id, null, "UPDATE_SYSTEM", "更新系统: " + system.getName(), null, true);
        return ApiResponse.success(system);
    }

    @Operation(summary = "系统详情")
    @GetMapping("/{id}")
    public ApiResponse<SystemApplication> getSystem(@PathVariable Long id) {
        SystemApplication system = systemApplicationService.getById(id);
        return ApiResponse.success(system);
    }

    @Operation(summary = "系统分页查询（带聚合指标）")
    @GetMapping
    public ApiResponse<PageResult<SystemSummaryVO>> listSystems(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String state) {
        Page<SystemSummaryVO> page = systemApplicationService.listSystemsPage(current, size, name, owner, status, state);
        PageResult<SystemSummaryVO> result = new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(), page.getRecords());
        return ApiResponse.success(result);
    }

    /**
     * 旧 status 切换端点（1=启用 / 0=停用），已废弃，新代码请用 {@link #changeState}
     */
    @Operation(summary = "[已废弃] 启用/停用系统")
    @Deprecated
    @PutMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        systemApplicationService.changeStatus(id, status);
        operationLogService.logOperation(id, null, "CHANGE_SYSTEM_STATUS", "修改系统状态为: " + (status == 1 ? "启用" : "停用"), null, true);
        return ApiResponse.success();
    }

    /**
     * 状态机切换端点（仅允许 ACTIVE / DISABLED 两个目标态）
     */
    @Operation(summary = "状态机切换：ACTIVE / DISABLED")
    @PutMapping("/{id}/state")
    public ApiResponse<Void> changeState(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String target = body == null ? null : body.get("target");
        systemApplicationService.changeState(id, target);
        operationLogService.logOperation(id, null, "CHANGE_SYSTEM_STATE", "修改系统状态为: " + target, null, true);
        return ApiResponse.success();
    }

    @Operation(summary = "软删除系统（级联软删其下所有代码库）")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSystem(@PathVariable Long id) {
        systemApplicationService.softDeleteSystem(id);
        operationLogService.logOperation(id, null, "DELETE_SYSTEM", "软删除系统 ID=" + id + "（含其下代码库）", null, true);
        return ApiResponse.success();
    }
}
