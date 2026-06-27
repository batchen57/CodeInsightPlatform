package com.company.codeinsight.modules.draft.controller;

import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.modules.draft.entity.*;
import com.company.codeinsight.modules.draft.service.DraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识草稿复核管理控制器
 * 提供任务工作区获取、草稿读取、物理保存、自动暂存、确认通过、驳回、修订历史及审核意见等 REST API 端点。
 */
@Tag(name = "草稿复核管理", description = "草稿区工作流、版本保存、自动缓存及在线编辑接口")
@RestController
@RequestMapping("/drafts")
public class DraftController {

    @Autowired
    private DraftService draftService;

    /**
     * 根据分析任务 ID 获取生成关联的工作区元数据及包含的草稿列表
     *
     * @param taskId 任务 ID
     * @return 返回 map，包含 workspace 详情和 drafts 列表
     */
    @Operation(summary = "根据任务 ID 获取工作区详情")
    @GetMapping("/workspace/task/{taskId}")
    public ApiResponse<Map<String, Object>> getWorkspaceByTask(@PathVariable Long taskId) {
        DraftWorkspace ws = draftService.getWorkspaceByTaskId(taskId);
        if (ws == null) {
            return ApiResponse.success(null);
        }
        List<KnowledgeDraft> list = draftService.listDraftsByWorkspace(ws.getId());
        
        Map<String, Object> res = new HashMap<>();
        res.put("workspace", ws);
        res.put("drafts", list);
        return ApiResponse.success(res);
    }

    /**
     * 查询指定工作区下的所有草稿模块基本信息
     */
    @Operation(summary = "查询工作区下的草稿列表")
    @GetMapping("/workspace/{workspaceId}")
    public ApiResponse<List<KnowledgeDraft>> listDrafts(@PathVariable Long workspaceId) {
        List<KnowledgeDraft> list = draftService.listDraftsByWorkspace(workspaceId);
        return ApiResponse.success(list);
    }

    /**
     * 读取指定草稿的 Markdown 正文全文内容
     */
    @Operation(summary = "读取草稿正文内容")
    @GetMapping("/{id}/content")
    public ApiResponse<String> getContent(@PathVariable Long id) {
        String content = draftService.getDraftContent(id);
        return ApiResponse.success(content);
    }

    /**
     * 手动编辑物理保存草稿：物理写入文件并写入修订记录表
     */
    @Operation(summary = "保存修改草稿")
    @PostMapping("/{id}/save")
    public ApiResponse<Void> save(
            @PathVariable Long id,
            @RequestParam String content,
            @RequestParam(required = false, defaultValue = "Admin") String author,
            @RequestParam(required = false, defaultValue = "手动编辑保存") String remark) {
        draftService.saveDraft(id, content, author, remark);
        return ApiResponse.success();
    }

    /**
     * 自动暂存草稿内容：1.8s防抖自动保存，快速将草稿缓存写入临时缓冲介质以防止意外丢失
     */
    @Operation(summary = "自动暂存草稿内容")
    @PostMapping("/{id}/autosave")
    public ApiResponse<Void> autoSave(
            @PathVariable Long id,
            @RequestParam String content,
            @RequestParam(required = false, defaultValue = "Admin") String author) {
        draftService.autoSaveDraft(id, content, author);
        return ApiResponse.success();
    }

    /**
     * 确认并通过草稿：将草稿置为 CONFIRMED (已确认) 状态
     */
    @Operation(summary = "确认并通过草稿")
    @PostMapping("/{id}/confirm")
    public ApiResponse<Void> confirm(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Admin") String author) {
        draftService.confirmDraft(id, author);
        return ApiResponse.success();
    }

    /**
     * 驳回/打回草稿：记录具体批注，并将草稿退回至 REJECTED (已驳回) 状态
     */
    @Operation(summary = "驳回/打回草稿")
    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(
            @PathVariable Long id,
            @RequestParam String comment,
            @RequestParam(required = false, defaultValue = "Admin") String author) {
        draftService.rejectDraft(id, author, comment);
        return ApiResponse.success();
    }

    /**
     * 查询指定草稿的历史版本修改修订记录列表
     */
    @Operation(summary = "查询修订历史记录")
    @GetMapping("/{id}/revisions")
    public ApiResponse<List<DraftRevision>> getRevisions(@PathVariable Long id) {
        List<DraftRevision> list = draftService.getRevisions(id);
        return ApiResponse.success(list);
    }

    /**
     * 查询指定草稿的人工评审复核批注意见列表
     */
    @Operation(summary = "查询评审意见记录")
    @GetMapping("/{id}/comments")
    public ApiResponse<List<DraftReviewComment>> getComments(@PathVariable Long id) {
        List<DraftReviewComment> list = draftService.getComments(id);
        return ApiResponse.success(list);
    }

    /**
     * 查询该草稿模块关联的底层代码来源文件及行号范围列表
     */
    @Operation(summary = "查询源文件引用")
    @GetMapping("/{id}/references")
    public ApiResponse<List<DraftSourceReference>> getReferences(@PathVariable Long id) {
        List<DraftSourceReference> list = draftService.getSourceReferences(id);
        return ApiResponse.success(list);
    }
}

