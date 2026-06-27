package com.company.codeinsight.modules.draft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.draft.entity.KnowledgeDraft;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模块知识草稿数据持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_knowledge_draft 表的 CRUD 操作。
 */
@Mapper
public interface KnowledgeDraftMapper extends BaseMapper<KnowledgeDraft> {
}

