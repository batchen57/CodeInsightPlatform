package com.company.codeinsight.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.log.service.OperationLogService;
import com.company.codeinsight.modules.system.entity.SystemApplication;
import com.company.codeinsight.modules.system.service.SystemApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 业务系统应用管理控制器
 * 提供接入应用系统的新增登记、信息编辑、详情获取、分页模糊条件筛选以及启停状态维护端点。
 */
@Tag(name = "系统管理", description = "系统的增删改查及启停接口")
@RestController
@RequestMapping("/systems")
@Validated
public class SystemApplicationController {

    @Autowired
    private SystemApplicationService systemApplicationService;

    @Autowired
    private OperationLogService operationLogService;

    /**
     * 新增接入的业务系统配置，默认初始为启用状态
     */
    @Operation(summary = "新增系统")
    @PostMapping
    public ApiResponse<SystemApplication> createSystem(@Valid @RequestBody SystemApplication system) {
        system.setId(null);
        system.setStatus(1); // 默认启用
        systemApplicationService.save(system);
        operationLogService.logOperation(system.getId(), null, "CREATE_SYSTEM", "创建系统: " + system.getName(), null, true);
        return ApiResponse.success(system);
    }

    /**
     * 编辑更新已有业务系统配置数据
     */
    @Operation(summary = "编辑系统")
    @PutMapping("/{id}")
    public ApiResponse<SystemApplication> updateSystem(@PathVariable Long id, @Valid @RequestBody SystemApplication system) {
        system.setId(id);
        systemApplicationService.updateById(system);
        operationLogService.logOperation(id, null, "UPDATE_SYSTEM", "更新系统: " + system.getName(), null, true);
        return ApiResponse.success(system);
    }

    /**
     * 获取指定 ID 的接入业务系统配置详情
     */
    @Operation(summary = "系统详情")
    @GetMapping("/{id}")
    public ApiResponse<SystemApplication> getSystem(@PathVariable Long id) {
        SystemApplication system = systemApplicationService.getById(id);
        return ApiResponse.success(system);
    }

    /**
     * 分页、多条件搜索系统列表
     */
    @Operation(summary = "系统分页查询")
    @GetMapping
    public ApiResponse<PageResult<SystemApplication>> listSystems(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) Integer status) {
        Page<SystemApplication> page = systemApplicationService.listSystemsPage(current, size, name, owner, status);
        PageResult<SystemApplication> result = new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(), page.getRecords());
        return ApiResponse.success(result);
    }

    /**
     * 启用或停用某个应用系统（status: 1-启用, 0-停用）
     */
    @Operation(summary = "启用/停用系统")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        systemApplicationService.changeStatus(id, status);
        operationLogService.logOperation(id, null, "CHANGE_SYSTEM_STATUS", "修改系统状态为: " + (status == 1 ? "启用" : "停用"), null, true);
        return ApiResponse.success();
    }
}

