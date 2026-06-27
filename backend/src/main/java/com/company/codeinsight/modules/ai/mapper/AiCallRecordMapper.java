package com.company.codeinsight.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.ai.entity.AiCallRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 调用历史明细数据持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_ai_call_record 表的单表常规 CRUD 操作。
 */
@Mapper
public interface AiCallRecordMapper extends BaseMapper<AiCallRecord> {
}

