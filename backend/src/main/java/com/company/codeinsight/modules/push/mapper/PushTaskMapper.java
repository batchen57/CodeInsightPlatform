package com.company.codeinsight.modules.push.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.push.entity.PushTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 推送任务 Mapper 接口
 * 继承 MyBatis-Plus BaseMapper 提供通用 CRUD 操作。
 */
@Mapper
public interface PushTaskMapper extends BaseMapper<PushTask> {
}
