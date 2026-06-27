package com.company.codeinsight.modules.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.token.entity.TokenUsageAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * Token 用量审计持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_token_usage_audit 表的 CRUD 操作。
 */
@Mapper
public interface TokenUsageAuditMapper extends BaseMapper<TokenUsageAudit> {
}

