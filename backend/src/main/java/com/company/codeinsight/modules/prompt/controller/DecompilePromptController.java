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

/**
 * AI 提示词模板管理控制器
 * 提供提示词配置的创建、版本编辑递增、克隆复制、启用状态切换、删除约束、以及基于样本代码的测试试跑 REST API 端点。
 */
@Tag(name = "提示词管理", description = "用于代码总结的 AI 提示词模板管理")
@RestController
@RequestMapping("/prompts")
@Validated
public class DecompilePromptController {

    @Autowired
    private DecompilePromptService decompilePromptService;

    @Autowired
    private OperationLogService operationLogService;

    /**
     * 创建新的提示词模板，初始默认版本号为 1 且启用
     */
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

    /**
     * 更新已存在的提示词模板内容，自动递增版本号做审计回溯
     */
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

    /**
     * 克隆指定的提示词模板，生成一份副本作为基础模板
     */
    @Operation(summary = "克隆/复制提示词模板")
    @PostMapping("/{id}/clone")
    public ApiResponse<DecompilePrompt> clonePrompt(@PathVariable Long id) {
        DecompilePrompt cloned = decompilePromptService.clonePrompt(id);
        operationLogService.logOperation(null, null, "CLONE_PROMPT", "克隆提示词模板, 源模板ID: " + id + ", 新模板ID: " + cloned.getId(), null, true);
        return ApiResponse.success(cloned);
    }

    /**
     * 修改提示词的使用启用状态（启用 / 禁用）
     */
    @Operation(summary = "修改提示词启用状态")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        decompilePromptService.changeStatus(id, status);
        operationLogService.logOperation(null, null, "CHANGE_PROMPT_STATUS", "修改提示词模板状态, ID: " + id + ", 状态: " + (status == 1 ? "启用" : "禁用"), null, true);
        return ApiResponse.success();
    }

    /**
     * 获取指定 ID 提示词模板详情
     */
    @Operation(summary = "提示词模板详情")
    @GetMapping("/{id}")
    public ApiResponse<DecompilePrompt> getPrompt(@PathVariable Long id) {
        DecompilePrompt prompt = decompilePromptService.getById(id);
        return ApiResponse.success(prompt);
    }

    /**
     * 分页多条件检索提示词模板
     *
     * @param promptType 提示词用途过滤：MODULARIZE-模块提取 / DOCUMENT_GENERATION-文档生成；不传则查询全部
     */
    @Operation(summary = "提示词模板分页查询")
    @GetMapping
    public ApiResponse<PageResult<DecompilePrompt>> listPrompts(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String promptType) {
        Page<DecompilePrompt> page = decompilePromptService.listPromptsPage(current, size, name, status, promptType);
        PageResult<DecompilePrompt> result = new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(), page.getRecords());
        return ApiResponse.success(result);
    }

    /**
     * 删除特定的提示词模板配置（防止误删默认首选项）
     */
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

    /**
     * 调试试跑提示词模板：提交测试源码片断，实时调度大模型分析返回归纳结果以辅助调试
     */
    @Operation(summary = "试跑提示词模板")
    @PostMapping("/{id}/test-run")
    public ApiResponse<PromptTestResultDto> testRun(@PathVariable Long id, @RequestBody TestRunRequest request) {
        PromptTestResultDto result = decompilePromptService.testRun(id, request.getSampleCode(), request.getModelId());
        operationLogService.logOperation(null, null, "TEST_RUN_PROMPT", "试跑提示词模板, ID: " + id + ", 消耗时间: " + result.getDurationMs() + "ms", null, true);
        return ApiResponse.success(result);
    }

    /**
     * 试跑测试请求载荷类
     */
    @Data
    public static class TestRunRequest {
        /**
         * 用于测试输入的一段样例源码（如 Java 方法代码）
         */
        private String sampleCode;
        /**
         * 所需测试调用的 AI 大模型 ID
         */
        private Long modelId;
    }
}

