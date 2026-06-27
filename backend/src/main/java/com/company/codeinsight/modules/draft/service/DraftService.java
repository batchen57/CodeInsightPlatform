package com.company.codeinsight.modules.draft.service;

import com.company.codeinsight.modules.draft.entity.*;
import java.util.List;

/**
 * 知识草稿复核管理服务接口
 * 负责知识工作区获取、草稿编辑保存、自动暂存、确认发布与驳回退回等业务逻辑的定义。
 */
public interface DraftService {

    /**
     * 查询指定工作区下的所有模块草稿
     */
    List<KnowledgeDraft> listDraftsByWorkspace(Long workspaceId);

    /**
     * 根据任务 ID 查询对应生成的工作区信息
     */
    DraftWorkspace getWorkspaceByTaskId(Long taskId);

    /**
     * 获取单个草稿实体的基本信息
     */
    KnowledgeDraft getDraftById(Long draftId);

    /**
     * 读取指定草稿存储在物理目录下的 Markdown 正文全文内容
     */
    String getDraftContent(Long draftId);

    /**
     * 手动编辑保存草稿：同步写入物理磁盘文件，并在修订记录表中插入修改记录
     */
    void saveDraft(Long draftId, String content, String author, String remark);

    /**
     * 自动暂存草稿：防抖极速暂存，临时缓存写入
     */
    void autoSaveDraft(Long draftId, String content, String author);

    /**
     * 确认并通过该草稿：将状态置为 CONFIRMED
     */
    void confirmDraft(Long draftId, String author);

    /**
     * 驳回/退回草稿：记录具体批注意见并将状态回退为 REJECTED
     */
    void rejectDraft(Long draftId, String author, String comment);

    /**
     * 查询草稿的所有修订版本历史
     */
    List<DraftRevision> getRevisions(Long draftId);

    /**
     * 查询草稿的所有评审反馈意见列表
     */
    List<DraftReviewComment> getComments(Long draftId);

    /**
     * 查询该草稿模块涉及的代码源文件引用指引列表
     */
    List<DraftSourceReference> getSourceReferences(Long draftId);
}

