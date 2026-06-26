package com.company.codeinsight.modules.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.model.entity.AiModel;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI模型配置Mapper接口
 */
@Mapper
public interface AiModelMapper extends BaseMapper<AiModel> {
}
