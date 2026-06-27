package com.company.codeinsight.modules.model.controller;

import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.modules.log.service.OperationLogService;
import com.company.codeinsight.modules.model.entity.AiModel;
import com.company.codeinsight.modules.model.service.AiModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
    private OperationLogService operationLogService;

    /**
     * 查询系统配置的所有可用 AI 大模型列表（按排序优先级展示）
     */
    @Operation(summary = "查询所有AI模型")
    @GetMapping
    public ApiResponse<List<AiModel>> listAllModels() {
        List<AiModel> list = aiModelService.listAllModelsSorted();
        return ApiResponse.success(list);
    }

    /**
     * 获取指定 ID 的大模型详细配置参数
     */
    @Operation(summary = "查询指定AI模型详情")
    @GetMapping("/{id}")
    public ApiResponse<AiModel> getModelDetail(@PathVariable Long id) {
        AiModel model = aiModelService.getById(id);
        return ApiResponse.success(model);
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
        return ApiResponse.success(model);
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
        return ApiResponse.success(model);
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
}

