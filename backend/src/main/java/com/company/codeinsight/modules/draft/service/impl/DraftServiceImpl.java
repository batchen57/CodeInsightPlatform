package com.company.codeinsight.modules.draft.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.draft.entity.*;
import com.company.codeinsight.modules.draft.mapper.*;
import com.company.codeinsight.modules.draft.service.DraftService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import com.company.codeinsight.modules.repository.entity.CodeRepository;
import com.company.codeinsight.modules.repository.mapper.CodeRepositoryMapper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识草稿及评审工作区服务实现类
 * 负责平台草稿库的修改保存、自动缓存（基于本地 JVM 内存）、修订版本行级差异计算、驳回/意见评审关联和确认归档。
 */
@Slf4j
@Service
public class DraftServiceImpl implements DraftService {

    @Autowired
    private DraftWorkspaceMapper workspaceMapper;

    @Autowired
    private KnowledgeDraftMapper draftMapper;

    @Autowired
    private DraftRevisionMapper revisionMapper;

    @Autowired
    private DraftReviewCommentMapper commentMapper;

    @Autowired
    private DraftSourceReferenceMapper referenceMapper;

    @Autowired
    private CodeRepositoryMapper repositoryMapper;

    // 本地 JVM 内存缓存容器（用于草稿自动保存）
    private static final ConcurrentHashMap<Long, String> memoryAutoSave = new ConcurrentHashMap<>();

    /**
     * 查询指定评审工作区下的所有草稿
     */
    @Override
    public List<KnowledgeDraft> listDraftsByWorkspace(Long workspaceId) {
        return draftMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDraft>().eq(KnowledgeDraft::getWorkspaceId, workspaceId)
        );
    }

    /**
     * 获取指定任务的评审工作区实体
     */
    @Override
    public DraftWorkspace getWorkspaceByTaskId(Long taskId) {
        return workspaceMapper.selectOne(
                new LambdaQueryWrapper<DraftWorkspace>().eq(DraftWorkspace::getTaskId, taskId)
        );
    }

    /**
     * 获取指定 ID 的知识草稿元数据
     */
    @Override
    public KnowledgeDraft getDraftById(Long draftId) {
        return draftMapper.selectById(draftId);
    }

    /**
     * 获取草稿的最新的正文内容
     * 优先从自动保存的临时缓存（JVM 内存）中提取尚未手动提交的修改，无临时缓存则读取物理存储正文。
     *
     * @param draftId 草稿 ID
     * @return Markdown 格式的文档内容
     */
    @Override
    public String getDraftContent(Long draftId) {
        KnowledgeDraft draft = draftMapper.selectById(draftId);
        if (draft == null) {
            throw new BusinessException("草稿记录不存在");
        }

        // 1. 尝试从内存自动保存缓存中获取
        String autoSavedContent = memoryAutoSave.get(draftId);

        // 2. 如果找到了有效的自动保存缓存，直接返回该临时内容
        if (StringUtils.hasText(autoSavedContent)) {
            return autoSavedContent;
        }

        // 3. 若无自动保存痕迹，从原始 URI 地址读取磁盘上的正式草稿文件
        try {
            File file = new File(URI.create(draft.getContentUri()));
            if (file.exists()) {
                return Files.readString(file.toPath());
            }
        } catch (Exception e) {
            log.error("从 URI 读取草稿正文失败: {}", draft.getContentUri(), e);
        }
        return "# 空文档";
    }

    /**
     * 手动保存草稿修改并进行物理文件落盘
     * 清空相关的临时自动保存缓存，计算并持久化修订记录（包含新增/删除行数统计）。
     *
     * @param draftId 草稿 ID
     * @param content 修改后的新文档内容
     * @param author  保存操作的负责人用户名
     * @param remark  用户填写的保存备注（修改日志）
     */
    @Override
    @Transactional
    public void saveDraft(Long draftId, String content, String author, String remark) {
        KnowledgeDraft draft = draftMapper.selectById(draftId);
        if (draft == null) {
            throw new BusinessException("草稿不存在");
        }

        try {
            // 读取原有的物理正文以对比差异
            File file = new File(URI.create(draft.getContentUri()));
            String originalContent = "";
            if (file.exists()) {
                originalContent = Files.readString(file.toPath());
            }

            // 物理覆盖写入新正文
            Files.writeString(file.toPath(), content);

            // 成功保存后，清理内存自动保存缓存
            memoryAutoSave.remove(draftId);

            // 计算 MD5 哈希校验和，状态流转为 REVISED (已修订)
            String hash = DigestUtils.md5DigestAsHex(content.getBytes());
            draft.setHash(hash);
            draft.setStatus("REVISED");
            draft.setUpdatedAt(LocalDateTime.now());
            draftMapper.updateById(draft);

            // 差分对比原文本与新文本，生成行级增改统计信息
            String diffSummary = computeDiffSummary(originalContent, content);
            String finalRemark = (remark != null ? remark : "保存修改") + diffSummary;

            // 自动将本次修改记录归档到 ci_draft_revision 修订表中
            DraftRevision revision = new DraftRevision();
            revision.setDraftId(draftId);
            revision.setContentUri(draft.getContentUri());
            revision.setAuthor(author);
            revision.setRemark(finalRemark);
            revision.setCreatedAt(LocalDateTime.now());
            revisionMapper.insert(revision);

            // 若代码库是本地路径，同时在本地代码库指定目录下更新保存一份备份
            try {
                DraftWorkspace ws = workspaceMapper.selectById(draft.getWorkspaceId());
                if (ws != null) {
                    CodeRepository repo = repositoryMapper.selectById(ws.getRepositoryId());
                    if (repo != null && StringUtils.hasText(repo.getGitUrl())) {
                        File localRepoDir = new File(repo.getGitUrl());
                        if (localRepoDir.exists() && localRepoDir.isDirectory()) {
                            File targetDraftDir = new File(localRepoDir, "docs/code-insight/drafts");
                            if (!targetDraftDir.exists()) {
                                targetDraftDir.mkdirs();
                            }
                            File targetDraftFile = new File(targetDraftDir, draft.getModuleName().replaceAll("[\\s/\\(\\)]", "_") + ".md");
                            Files.writeString(targetDraftFile.toPath(), content);
                            log.info("本地模式：用户保存修改，同步更新至本地代码库指定目录：{}", targetDraftFile.getAbsolutePath());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("同步更新草稿文档至本地代码库指定目录失败", e);
            }

        } catch (IOException e) {
            log.error("保存草稿内容失败", e);
            throw new BusinessException("保存草稿正文失败");
        }
    }

    /**
     * 临时自动保存草稿
     * 避免网页崩溃导致数据丢失，缓存至本地 JVM 内存。
     */
    @Override
    public void autoSaveDraft(Long draftId, String content, String author) {
        memoryAutoSave.put(draftId, content);
    }

    /**
     * 确认并归档单篇草稿文档
     * 如果该评审区下的所有模块草稿均已被确认（CONFIRMED），则同步将当前评审工作区整体晋升为 COMPLETED 状态。
     */
    @Override
    @Transactional
    public void confirmDraft(Long draftId, String author) {
        KnowledgeDraft draft = draftMapper.selectById(draftId);
        if (draft == null) {
            throw new BusinessException("草稿不存在");
        }

        // 修改状态为 CONFIRMED
        draft.setStatus("CONFIRMED");
        draft.setUpdatedAt(LocalDateTime.now());
        draftMapper.updateById(draft);

        // 检查同一个工作区下的所有草稿状态是否都已经确认
        List<KnowledgeDraft> drafts = listDraftsByWorkspace(draft.getWorkspaceId());
        boolean allConfirmed = drafts.stream().allMatch(d -> "CONFIRMED".equals(d.getStatus()));
        if (allConfirmed) {
            DraftWorkspace ws = workspaceMapper.selectById(draft.getWorkspaceId());
            if (ws != null) {
                // 整组评审流程通关，工作区标记完成
                ws.setStatus("COMPLETED");
                ws.setUpdatedAt(LocalDateTime.now());
                workspaceMapper.updateById(ws);
            }
        }
    }

    /**
     * 驳回指定草稿，将其流转回 REJECTED 状态，并强制记录评审反馈意见
     */
    @Override
    @Transactional
    public void rejectDraft(Long draftId, String author, String comment) {
        KnowledgeDraft draft = draftMapper.selectById(draftId);
        if (draft == null) {
            throw new BusinessException("草稿不存在");
        }

        draft.setStatus("REJECTED");
        draft.setUpdatedAt(LocalDateTime.now());
        draftMapper.updateById(draft);

        // 新增一条复核驳回评审意见
        DraftReviewComment rc = new DraftReviewComment();
        rc.setDraftId(draftId);
        rc.setAuthor(author);
        rc.setComment(comment);
        rc.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(rc);
    }

    /**
     * 获取指定草稿的所有修订历史
     */
    @Override
    public List<DraftRevision> getRevisions(Long draftId) {
        return revisionMapper.selectList(
                new LambdaQueryWrapper<DraftRevision>().eq(DraftRevision::getDraftId, draftId).orderByDesc(DraftRevision::getCreatedAt)
        );
    }

    /**
     * 获取指定草稿的所有评审反馈与驳回意见
     */
    @Override
    public List<DraftReviewComment> getComments(Long draftId) {
        return commentMapper.selectList(
                new LambdaQueryWrapper<DraftReviewComment>().eq(DraftReviewComment::getDraftId, draftId).orderByDesc(DraftReviewComment::getCreatedAt)
        );
    }

    /**
     * 获取指定草稿的所有关联代码来源引用（文件名及行范围）
     */
    @Override
    public List<DraftSourceReference> getSourceReferences(Long draftId) {
        return referenceMapper.selectList(
                new LambdaQueryWrapper<DraftSourceReference>().eq(DraftSourceReference::getDraftId, draftId)
        );
    }

    /**
     * 轻量级差分差异计算函数
     * 通过集合取差集，快速计算保存内容相比于历史物理内容的行级修改差异。
     */
    private String computeDiffSummary(String oldContent, String newContent) {
        if (oldContent == null) oldContent = "";
        if (newContent == null) newContent = "";
        // 按平台自适应换行符分割文件行
        String[] oldLines = oldContent.split("\\R");
        String[] newLines = newContent.split("\\R");

        java.util.Set<String> oldSet = new java.util.HashSet<>(java.util.Arrays.asList(oldLines));
        java.util.Set<String> newSet = new java.util.HashSet<>(java.util.Arrays.asList(newLines));

        // 统计新增行：在新内容中但不在老内容中出现的行
        int added = 0;
        for (String line : newLines) {
            if (!oldSet.contains(line)) {
                added++;
            }
        }
        // 统计删除行：在老内容中但未在新内容中出现的行
        int deleted = 0;
        for (String line : oldLines) {
            if (!newSet.contains(line)) {
                deleted++;
            }
        }
        return String.format(" [新增 %d 行, 删除 %d 行]", added, deleted);
    }
}
