package com.company.codeinsight.modules.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.model.entity.AiModelPreset;
import com.company.codeinsight.modules.model.mapper.AiModelPresetMapper;
import com.company.codeinsight.modules.model.service.AiModelPresetService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 模型预设模板服务实现。
 */
@Service
public class AiModelPresetServiceImpl extends ServiceImpl<AiModelPresetMapper, AiModelPreset> implements AiModelPresetService {

    @Override
    public List<AiModelPreset> listEnabledPresetsSorted() {
        LambdaQueryWrapper<AiModelPreset> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiModelPreset::getStatus, 1)
                    .orderByAsc(AiModelPreset::getSortOrder)
                    .orderByDesc(AiModelPreset::getId);
        return this.list(queryWrapper);
    }

    @Override
    public List<AiModelPreset> listAllPresetsSorted() {
        LambdaQueryWrapper<AiModelPreset> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(AiModelPreset::getSortOrder)
                    .orderByDesc(AiModelPreset::getId);
        return this.list(queryWrapper);
    }

    @Override
    public boolean savePreset(AiModelPreset preset) {
        if (preset.getStatus() == null) {
            preset.setStatus(1);
        }
        if (preset.getSortOrder() == null) {
            preset.setSortOrder(0);
        }
        return this.save(preset);
    }

    @Override
    public boolean updatePreset(AiModelPreset preset) {
        if (preset.getStatus() == null) {
            preset.setStatus(1);
        }
        if (preset.getSortOrder() == null) {
            preset.setSortOrder(0);
        }
        return this.updateById(preset);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态非法，只能为 0 或 1");
        }
        AiModelPreset preset = this.getById(id);
        if (preset == null) {
            throw new BusinessException("预设模板不存在");
        }
        preset.setStatus(status);
        this.updateById(preset);
    }
}
