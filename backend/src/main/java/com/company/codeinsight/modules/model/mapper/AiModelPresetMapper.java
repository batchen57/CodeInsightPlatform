package com.company.codeinsight.modules.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.model.entity.AiModelPreset;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 模型预设模板持久层 Mapper。
 */
@Mapper
public interface AiModelPresetMapper extends BaseMapper<AiModelPreset> {
}
