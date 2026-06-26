package com.company.codeinsight.modules.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.model.entity.AiModel;
import java.util.List;

/**
 * AI模型配置服务接口
 */
public interface AiModelService extends IService<AiModel> {
    
    /**
     * 获取所有模型，按 sortOrder 排序
     */
    List<AiModel> listAllModelsSorted();

    /**
     * 保存模型（包含默认模型排他性处理）
     */
    boolean saveModel(AiModel model);

    /**
     * 更新模型（包含默认模型排他性处理）
     */
    boolean updateModel(AiModel model);
}
