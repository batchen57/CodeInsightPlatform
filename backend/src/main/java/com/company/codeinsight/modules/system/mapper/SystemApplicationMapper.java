package com.company.codeinsight.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.system.entity.SystemApplication;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务系统应用持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_system 数据库表的常规单表 CRUD。
 */
@Mapper
public interface SystemApplicationMapper extends BaseMapper<SystemApplication> {
}

