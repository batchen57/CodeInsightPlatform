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
 * AI 大语言模型配置与选取服务实现类
 * 负责模型按权重展示排序，以及写入/更新大模型时排他性地保证仅有一个默认大模型。
 */
@Service
public class AiModelServiceImpl extends ServiceImpl<AiModelMapper, AiModel> implements AiModelService {

    /**
     * 按照 sortOrder 升序、主键自增 ID 降序排列获取全部 AI 模型
     */
    @Override
    public List<AiModel> listAllModelsSorted() {
        LambdaQueryWrapper<AiModel> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(AiModel::getSortOrder)
                    .orderByDesc(AiModel::getId);
        return this.list(queryWrapper);
    }

    /**
     * 新增大语言模型
     * 若本模型声明为默认（isDefault=true），则自动将其它已存在的默认大模型降级。
     */
    @Override
    @Transactional
    public boolean saveModel(AiModel model) {
        if ("true".equals(model.getIsDefault())) {
            clearOtherDefaults(null);
        }
        return this.save(model);
    }

    /**
     * 修改已有大模型
     * 若修改后本模型被置为默认，自动将其它已存在大模型设为非默认。
     */
    @Override
    @Transactional
    public boolean updateModel(AiModel model) {
        if ("true".equals(model.getIsDefault())) {
            clearOtherDefaults(model.getId());
        }
        return this.updateById(model);
    }

    /**
     * 辅助排他性机制：批量清空其它大模型的默认标志
     *
     * @param excludeId 需要排斥不清除的当前模型 ID
     */
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

