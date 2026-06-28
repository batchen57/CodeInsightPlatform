package com.company.codeinsight.modules.model.controller;

import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.modules.log.service.OperationLogService;
import com.company.codeinsight.modules.model.dto.AiModelMetricSummary;
import com.company.codeinsight.modules.model.dto.AiModelMetricTrendPoint;
import com.company.codeinsight.modules.model.dto.AiModelTestResult;
import com.company.codeinsight.modules.model.entity.AiModel;
import com.company.codeinsight.modules.model.entity.AiModelPreset;
import com.company.codeinsight.modules.model.service.AiModelPresetService;
import com.company.codeinsight.modules.model.service.AiModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 模型配置管理控制器
 * 提供大语言模型配置的增删改查、排序和默认模型状态管理等 REST API。
 */
@Tag(name = "AI模型管理", description = "AI模型配置与状态管理")
@RestController
@RequestMapping("/models")
@Validated
public class AiModelController {

    @Autowired
    private AiModelService aiModelService;

    @Autowired
    private AiModelPresetService aiModelPresetService;

    @Autowired
    private OperationLogService operationLogService;

    /**
     * 查询系统配置的所有可用 AI 大模型列表（按排序优先级展示）
     */
    @Operation(summary = "查询所有AI模型")
    @GetMapping
    public ApiResponse<List<AiModel>> listAllModels() {
        List<AiModel> list = aiModelService.listAllModelsSorted();
        return ApiResponse.success(hideModelSecrets(list));
    }

    /**
     * 查询启用中的 AI 模型预设模板，用于前端新增模型时快速填充。
     */
    @Operation(summary = "查询AI模型预设模板")
    @GetMapping("/presets")
    public ApiResponse<List<AiModelPreset>> listModelPresets() {
        List<AiModelPreset> list = aiModelPresetService.listEnabledPresetsSorted();
        return ApiResponse.success(list);
    }

    /**
     * 查询全部 AI 模型预设模板，用于预设管理。
     */
    @Operation(summary = "查询全部AI模型预设模板")
    @GetMapping("/presets/all")
    public ApiResponse<List<AiModelPreset>> listAllModelPresets() {
        List<AiModelPreset> list = aiModelPresetService.listAllPresetsSorted();
        return ApiResponse.success(list);
    }

    /**
     * 汇总各模型累计调用次数、Token 与成本，用于模型配置表格展示。
     */
    @Operation(summary = "查询AI模型累计指标")
    @GetMapping("/metrics/summary")
    public ApiResponse<List<AiModelMetricSummary>> listModelMetricSummaries() {
        return ApiResponse.success(aiModelService.listMetricSummaries());
    }

    /**
     * 查询指定模型最近 N 天调用趋势，用于模型详情抽屉展示。
     */
    @Operation(summary = "查询AI模型调用趋势")
    @GetMapping("/{id}/metrics/trend")
    public ApiResponse<List<AiModelMetricTrendPoint>> getModelMetricTrend(
            @PathVariable Long id,
            @RequestParam(defaultValue = "7") Integer days) {
        return ApiResponse.success(aiModelService.getMetricTrend(id, days));
    }

    /**
     * 测试指定 AI 模型配置的连接可用性。
     */
    @Operation(summary = "测试AI模型连接")
    @PostMapping("/{id}/test")
    public ApiResponse<AiModelTestResult> testModelConnection(@PathVariable Long id) {
        AiModelTestResult result = aiModelService.testModelConnection(id);
        operationLogService.logOperation(null, null, "TEST_MODEL", "测试AI模型连接: " + id, null, result.getSuccess());
        return ApiResponse.success(result);
    }

    /**
     * 新增 AI 模型预设模板。预设只保存公开配置，不保存 API Key。
     */
    @Operation(summary = "新增AI模型预设模板")
    @PostMapping("/presets")
    public ApiResponse<AiModelPreset> createModelPreset(@Valid @RequestBody AiModelPreset preset) {
        preset.setId(null);
        aiModelPresetService.savePreset(preset);
        operationLogService.logOperation(null, null, "CREATE_MODEL_PRESET", "创建AI模型预设: " + preset.getName(), null, true);
        return ApiResponse.success(preset);
    }

    /**
     * 更新 AI 模型预设模板。
     */
    @Operation(summary = "更新AI模型预设模板")
    @PutMapping("/presets/{id}")
    public ApiResponse<AiModelPreset> updateModelPreset(@PathVariable Long id, @Valid @RequestBody AiModelPreset preset) {
        preset.setId(id);
        aiModelPresetService.updatePreset(preset);
        operationLogService.logOperation(null, null, "UPDATE_MODEL_PRESET", "更新AI模型预设: " + preset.getName(), null, true);
        return ApiResponse.success(aiModelPresetService.getById(id));
    }

    /**
     * 删除 AI 模型预设模板。
     */
    @Operation(summary = "删除AI模型预设模板")
    @DeleteMapping("/presets/{id}")
    public ApiResponse<Void> deleteModelPreset(@PathVariable Long id) {
        AiModelPreset preset = aiModelPresetService.getById(id);
        if (preset != null) {
            aiModelPresetService.removeById(id);
            operationLogService.logOperation(null, null, "DELETE_MODEL_PRESET", "删除AI模型预设: " + preset.getName(), null, true);
        }
        return ApiResponse.success();
    }

    /**
     * 启用 / 停用 AI 模型预设模板。
     */
    @Operation(summary = "启用/停用AI模型预设模板")
    @PutMapping("/presets/{id}/status")
    public ApiResponse<Void> changeModelPresetStatus(@PathVariable Long id, @RequestParam Integer status) {
        aiModelPresetService.changeStatus(id, status);
        operationLogService.logOperation(null, null, "CHANGE_MODEL_PRESET_STATUS", "修改AI模型预设状态: " + id, null, true);
        return ApiResponse.success();
    }

    /**
     * 获取指定 ID 的大模型详细配置参数
     */
    @Operation(summary = "查询指定AI模型详情")
    @GetMapping("/{id}")
    public ApiResponse<AiModel> getModelDetail(@PathVariable Long id) {
        AiModel model = aiModelService.getById(id);
        return ApiResponse.success(hideModelSecret(model));
    }

    /**
     * 新增注册一个 AI 大模型，配置端点和 API 密钥
     */
    @Operation(summary = "新增AI模型")
    @PostMapping
    public ApiResponse<AiModel> createModel(@Valid @RequestBody AiModel model) {
        model.setId(null);
        if (model.getSortOrder() == null) {
            model.setSortOrder(0);
        }
        if (model.getIsDefault() == null) {
            model.setIsDefault("false");
        }
        aiModelService.saveModel(model);
        operationLogService.logOperation(null, null, "CREATE_MODEL", "创建AI模型: " + model.getName(), null, true);
        return ApiResponse.success(hideModelSecret(model));
    }

    /**
     * 更新已有大模型的配置数据（如修改 API Key、切换接口 URL、重命名等）
     */
    @Operation(summary = "更新AI模型")
    @PutMapping("/{id}")
    public ApiResponse<AiModel> updateModel(@PathVariable Long id, @Valid @RequestBody AiModel model) {
        model.setId(id);
        aiModelService.updateModel(model);
        operationLogService.logOperation(null, null, "UPDATE_MODEL", "更新AI模型: " + model.getName(), null, true);
        return ApiResponse.success(hideModelSecret(aiModelService.getById(id)));
    }

    /**
     * 删除指定的大模型配置记录
     */
    @Operation(summary = "删除AI模型")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteModel(@PathVariable Long id) {
        AiModel model = aiModelService.getById(id);
        if (model != null) {
            aiModelService.removeById(id);
            operationLogService.logOperation(null, null, "DELETE_MODEL", "删除AI模型: " + model.getName(), null, true);
        }
        return ApiResponse.success();
    }

    /**
     * 启用 / 停用某个 AI 模型（status: 1-启用, 0-停用）
     * 停用时如果是默认模型，会自动让出默认身份
     */
    @Operation(summary = "启用/停用AI模型")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        AiModel before = aiModelService.getById(id);
        aiModelService.changeStatus(id, status);
        String modelName = before != null ? before.getName() : String.valueOf(id);
        String action = status == 1 ? "启用" : "停用";
        String detail = "修改AI模型[" + modelName + "]状态为: " + action;
        operationLogService.logOperation(null, null, "CHANGE_MODEL_STATUS", detail, null, true);
        return ApiResponse.success();
    }

    private List<AiModel> hideModelSecrets(List<AiModel> models) {
        return models.stream().map(this::hideModelSecret).toList();
    }

    private AiModel hideModelSecret(AiModel model) {
        if (model == null) {
            return null;
        }
        AiModel response = new AiModel();
        BeanUtils.copyProperties(model, response);
        response.setHasApiKey(StringUtils.hasText(model.getApiKey()));
        response.setApiKey(null);
        return response;
    }
}
