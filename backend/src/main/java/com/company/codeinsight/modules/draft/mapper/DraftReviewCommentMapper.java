package com.company.codeinsight.modules.draft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.draft.entity.DraftReviewComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 草稿评审复核意见数据持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_draft_review_comment 表的 CRUD 操作。
 */
@Mapper
public interface DraftReviewCommentMapper extends BaseMapper<DraftReviewComment> {
}

