package com.company.codeinsight.modules.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.model.entity.AiModel;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 大语言模型配置持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，用于常规的模型配置项单表增删改查。
 */
@Mapper
public interface AiModelMapper extends BaseMapper<AiModel> {
}

