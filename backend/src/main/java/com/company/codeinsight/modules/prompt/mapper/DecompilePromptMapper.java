package com.company.codeinsight.modules.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.prompt.entity.DecompilePrompt;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词模板配置数据持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_prompt 表的常规单表 CRUD 操纵。
 */
@Mapper
public interface DecompilePromptMapper extends BaseMapper<DecompilePrompt> {
}

