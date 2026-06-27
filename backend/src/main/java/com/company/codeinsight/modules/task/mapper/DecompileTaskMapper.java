package com.company.codeinsight.modules.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 反编译及分析任务数据持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_task 数据库表的单表 CRUD。
 */
@Mapper
public interface DecompileTaskMapper extends BaseMapper<DecompileTask> {
}

