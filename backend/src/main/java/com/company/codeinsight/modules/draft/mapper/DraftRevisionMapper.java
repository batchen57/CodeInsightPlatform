package com.company.codeinsight.modules.draft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.draft.entity.DraftRevision;
import org.apache.ibatis.annotations.Mapper;

/**
 * 草稿修订版本历史数据持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_draft_revision 表的 CRUD 操作。
 */
@Mapper
public interface DraftRevisionMapper extends BaseMapper<DraftRevision> {
}

