package com.company.codeinsight.modules.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.log.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统操作审计日志数据持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_operation_log 表的 CRUD 操作。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}

