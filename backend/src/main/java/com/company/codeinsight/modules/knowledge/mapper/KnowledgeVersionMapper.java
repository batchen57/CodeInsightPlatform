package com.company.codeinsight.modules.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.knowledge.entity.KnowledgeVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识版本发布数据持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_knowledge_version 表的 CRUD 操作。
 */
@Mapper
public interface KnowledgeVersionMapper extends BaseMapper<KnowledgeVersion> {
}

