package com.company.codeinsight.modules.draft.dto;

import lombok.Data;

/**
 * 草稿操作请求体 DTO（save / autosave / confirm 复用）
 * <p>将 content 从 URL 查询参数迁移到请求体，避免长 Markdown 文档
 * 因 URL 长度限制（Tomcat 默认 8KB）导致保存失败。</p>
 */
@Data
public class SaveDraftRequest {

    /**
     * Markdown 正文内容（save / autosave 时必填）
     */
    private String content;

    /**
     * 操作人（可选，默认 Admin）
     */
    private String author;

    /**
     * 修订说明（可选，saveDraft 时默认"手动编辑保存"）
     */
    private String remark;

    /**
     * 复核意见（可选，confirmDraft 时填写）
     */
    private String comment;
}
