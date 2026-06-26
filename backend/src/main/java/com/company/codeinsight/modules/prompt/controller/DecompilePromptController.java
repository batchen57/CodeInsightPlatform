package com.company.codeinsight.modules.prompt.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.log.service.OperationLogService;
import com.company.codeinsight.modules.prompt.dto.PromptTestResultDto;
import com.company.codeinsight.modules.prompt.entity.DecompilePrompt;
import com.company.codeinsight.modules.prompt.service.DecompilePromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "提示词管理", description = "用于代码总结的 AI 提示词模板管理")
@RestController
@RequestMapping("/prompts")
@Validated
public class DecompilePromptController {

    @Autowired
    private DecompilePromptService decompilePromptService;

    @Autowired
    private OperationLogService operationLogService;

    @Operation(summary = "创建提示词模板")
    @PostMapping
    public ApiResponse<DecompilePrompt> createPrompt(@Valid @RequestBody DecompilePrompt prompt) {
        prompt.setId(null);
        prompt.setVersion(1);
        prompt.setStatus(1); // 默认启用
        decompilePromptService.save(prompt);
        operationLogService.logOperation(null, null, "CREATE_PROMPT", "创建提示词模板: " + prompt.getName(), null, true);
        return ApiResponse.success(prompt);
    }

    @Operation(summary = "编辑提示词模板")
    @PutMapping("/{id}")
    public ApiResponse<DecompilePrompt> updatePrompt(@PathVariable Long id, @Valid @RequestBody DecompilePrompt prompt) {
        prompt.setId(id);
        // 如果编辑，将版本号递增
        DecompilePrompt existing = decompilePromptService.getById(id);
        if (existing != null) {
            prompt.setVersion(existing.getVersion() + 1);
        }
        decompilePromptService.updateById(prompt);
        operationLogService.logOperation(null, null, "UPDATE_PROMPT", "更新提示词模板: " + prompt.getName() + ", 新版本号: " + prompt.getVersion(), null, true);
        return ApiResponse.success(prompt);
    }

    @Operation(summary = "克隆/复制提示词模板")
    @PostMapping("/{id}/clone")
    public ApiResponse<DecompilePrompt> clonePrompt(@PathVariable Long id) {
        DecompilePrompt cloned = decompilePromptService.clonePrompt(id);
        operationLogService.logOperation(null, null, "CLONE_PROMPT", "克隆提示词模板, 源模板ID: " + id + ", 新模板ID: " + cloned.getId(), null, true);
        return ApiResponse.success(cloned);
    }

    @Operation(summary = "修改提示词启用状态")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        decompilePromptService.changeStatus(id, status);
        operationLogService.logOperation(null, null, "CHANGE_PROMPT_STATUS", "修改提示词模板状态, ID: " + id + ", 状态: " + (status == 1 ? "启用" : "禁用"), null, true);
        return ApiResponse.success();
    }

    @Operation(summary = "提示词模板详情")
    @GetMapping("/{id}")
    public ApiResponse<DecompilePrompt> getPrompt(@PathVariable Long id) {
        DecompilePrompt prompt = decompilePromptService.getById(id);
        return ApiResponse.success(prompt);
    }

    @Operation(summary = "提示词模板分页查询")
    @GetMapping
    public ApiResponse<PageResult<DecompilePrompt>> listPrompts(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        Page<DecompilePrompt> page = decompilePromptService.listPromptsPage(current, size, name, status);
        PageResult<DecompilePrompt> result = new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(), page.getRecords());
        return ApiResponse.success(result);
    }

    @Operation(summary = "删除提示词模板")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePrompt(@PathVariable Long id) {
        DecompilePrompt prompt = decompilePromptService.getById(id);
        if (prompt == null) {
            throw new BusinessException("提示词模板不存在");
        }
        if (prompt.getIsDefault() != null && prompt.getIsDefault() == 1) {
            throw new BusinessException("默认提示词模板不能删除，请先将其他模板设为默认");
        }
        decompilePromptService.removeById(id);
        operationLogService.logOperation(null, null, "DELETE_PROMPT", "删除提示词模板: " + prompt.getName(), null, true);
        return ApiResponse.success();
    }

    @Operation(summary = "试跑提示词模板")
    @PostMapping("/{id}/test-run")
    public ApiResponse<PromptTestResultDto> testRun(@PathVariable Long id, @RequestBody TestRunRequest request) {
        PromptTestResultDto result = decompilePromptService.testRun(id, request.getSampleCode(), request.getModelId());
        operationLogService.logOperation(null, null, "TEST_RUN_PROMPT", "试跑提示词模板, ID: " + id + ", 消耗时间: " + result.getDurationMs() + "ms", null, true);
        return ApiResponse.success(result);
    }

    @Data
    public static class TestRunRequest {
        private String sampleCode;
        private Long modelId;
    }
}
