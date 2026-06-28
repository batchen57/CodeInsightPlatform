package com.company.codeinsight.modules.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.model.entity.AiModelPreset;

import java.util.List;

/**
 * AI 模型预设模板服务接口。
 */
public interface AiModelPresetService extends IService<AiModelPreset> {

    /**
     * 查询启用中的预设模板，按 sortOrder 升序展示。
     */
    List<AiModelPreset> listEnabledPresetsSorted();

    /**
     * 查询全部预设模板，供管理界面使用。
     */
    List<AiModelPreset> listAllPresetsSorted();

    /**
     * 新增预设模板，补齐默认状态与排序值。
     */
    boolean savePreset(AiModelPreset preset);

    /**
     * 更新预设模板。
     */
    boolean updatePreset(AiModelPreset preset);

    /**
     * 切换预设模板启用状态。
     */
    void changeStatus(Long id, Integer status);
}
