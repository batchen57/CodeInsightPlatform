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
 * AI模型配置Controller
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

    @Operation(summary = "查询所有AI模型")
    @GetMapping
    public ApiResponse<List<AiModel>> listAllModels() {
        List<AiModel> list = aiModelService.listAllModelsSorted();
        return ApiResponse.success(list);
    }

    @Operation(summary = "查询指定AI模型详情")
    @GetMapping("/{id}")
    public ApiResponse<AiModel> getModelDetail(@PathVariable Long id) {
        AiModel model = aiModelService.getById(id);
        return ApiResponse.success(model);
    }

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

    @Operation(summary = "更新AI模型")
    @PutMapping("/{id}")
    public ApiResponse<AiModel> updateModel(@PathVariable Long id, @Valid @RequestBody AiModel model) {
        model.setId(id);
        aiModelService.updateModel(model);
        operationLogService.logOperation(null, null, "UPDATE_MODEL", "更新AI模型: " + model.getName(), null, true);
        return ApiResponse.success(model);
    }

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
