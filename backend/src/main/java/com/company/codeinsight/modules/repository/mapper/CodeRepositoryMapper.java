package com.company.codeinsight.modules.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.repository.entity.CodeRepository;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代码仓库配置持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_repository 数据库表常规增删改查。
 */
@Mapper
public interface CodeRepositoryMapper extends BaseMapper<CodeRepository> {
}

