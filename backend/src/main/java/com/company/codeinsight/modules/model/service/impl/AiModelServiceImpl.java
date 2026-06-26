package com.company.codeinsight.modules.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.codeinsight.modules.model.entity.AiModel;
import com.company.codeinsight.modules.model.mapper.AiModelMapper;
import com.company.codeinsight.modules.model.service.AiModelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI模型配置服务实现类
 */
@Service
public class AiModelServiceImpl extends ServiceImpl<AiModelMapper, AiModel> implements AiModelService {

    @Override
    public List<AiModel> listAllModelsSorted() {
        LambdaQueryWrapper<AiModel> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(AiModel::getSortOrder)
                    .orderByDesc(AiModel::getId);
        return this.list(queryWrapper);
    }

    @Override
    @Transactional
    public boolean saveModel(AiModel model) {
        if ("true".equals(model.getIsDefault())) {
            clearOtherDefaults(null);
        }
        return this.save(model);
    }

    @Override
    @Transactional
    public boolean updateModel(AiModel model) {
        if ("true".equals(model.getIsDefault())) {
            clearOtherDefaults(model.getId());
        }
        return this.updateById(model);
    }

    private void clearOtherDefaults(Long excludeId) {
        LambdaQueryWrapper<AiModel> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiModel::getIsDefault, "true");
        if (excludeId != null) {
            queryWrapper.ne(AiModel::getId, excludeId);
        }
        List<AiModel> defaults = this.list(queryWrapper);
        for (AiModel m : defaults) {
            m.setIsDefault("false");
            this.updateById(m);
        }
    }
}
