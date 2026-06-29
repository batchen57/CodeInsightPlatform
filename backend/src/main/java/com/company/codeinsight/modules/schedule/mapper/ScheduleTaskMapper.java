package com.company.codeinsight.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.schedule.entity.ScheduleTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScheduleTaskMapper extends BaseMapper<ScheduleTask> {
}