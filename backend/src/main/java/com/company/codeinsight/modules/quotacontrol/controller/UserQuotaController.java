package com.company.codeinsight.modules.quotacontrol.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.quotacontrol.dto.UserQuotaRequest;
import com.company.codeinsight.modules.quotacontrol.entity.UserQuota;
import com.company.codeinsight.modules.quotacontrol.service.UserQuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@Tag(name = "用户额度", description = "按用户维度的 Token 日/月额度配置")
@RestController
@RequestMapping("/user-quotas")
public class UserQuotaController {

    @Autowired
    private UserQuotaService userQuotaService;

    @Operation(summary = "分页查询")
    @GetMapping
    public ApiResponse<PageResult<UserQuota>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer enabled) {
        Page<UserQuota> p = userQuotaService.pageQuery(current, size, username, enabled);
        return ApiResponse.success(new PageResult<>(p.getTotal(), p.getSize(), p.getCurrent(), p.getRecords()));
    }

    @Operation(summary = "读取单条")
    @GetMapping("/{id}")
    public ApiResponse<UserQuota> get(@PathVariable Long id) {
        return ApiResponse.success(userQuotaService.getById(id));
    }

    @Operation(summary = "创建用户额度（按 userId 唯一）")
    @PostMapping
    public ApiResponse<UserQuota> create(@RequestBody UserQuotaRequest body) {
        if (body.getUserId() == null) {
            throw new BusinessException("userId 必填");
        }
        if (userQuotaService.findByUserId(body.getUserId()) != null) {
            throw new BusinessException("该用户已存在额度配置，请使用更新接口");
        }
        UserQuota q = new UserQuota();
        copyTo(body, q);
        userQuotaService.save(q);
        return ApiResponse.success(q);
    }

    @Operation(summary = "更新用户额度")
    @PutMapping("/{id}")
    public ApiResponse<UserQuota> update(@PathVariable Long id, @RequestBody UserQuotaRequest body) {
        UserQuota existing = userQuotaService.getById(id);
        if (existing == null) {
            throw new BusinessException("额度记录不存在");
        }
        copyTo(body, existing);
        userQuotaService.updateById(existing);
        return ApiResponse.success(existing);
    }

    @Operation(summary = "删除用户额度")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userQuotaService.removeById(id);
        return ApiResponse.success();
    }

    private void copyTo(UserQuotaRequest src, UserQuota dst) {
        if (src.getUserId() != null) dst.setUserId(src.getUserId());
        dst.setDailyTokenLimit(Objects.requireNonNullElse(src.getDailyTokenLimit(), 0));
        dst.setMonthlyTokenLimit(Objects.requireNonNullElse(src.getMonthlyTokenLimit(), 0));
        dst.setEnabled(Objects.requireNonNullElse(src.getEnabled(), 1));
        dst.setRemark(src.getRemark());
    }
}
