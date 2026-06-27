package com.company.codeinsight.modules.draft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.draft.entity.DraftWorkspace;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评审工作区数据持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_draft_workspace 表的 CRUD 操作。
 */
@Mapper
public interface DraftWorkspaceMapper extends BaseMapper<DraftWorkspace> {
}

