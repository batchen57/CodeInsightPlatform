package com.company.codeinsight.modules.callchain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.callchain.entity.MethodCall;
import org.apache.ibatis.annotations.Mapper;

/**
 * 方法调用链路 Mapper 接口
 * 继承 MyBatis-Plus BaseMapper，自动获得单表 CRUD 能力。
 */
@Mapper
public interface MethodCallMapper extends BaseMapper<MethodCall> {
}