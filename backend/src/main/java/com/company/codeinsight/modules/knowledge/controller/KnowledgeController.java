package com.company.codeinsight.modules.knowledge.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.knowledge.entity.KnowledgeVersion;
import com.company.codeinsight.modules.knowledge.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 知识推送与版本管理控制器
 * 提供知识库新版本创建、Git 推送提交、ZIP 压缩包二进制流导出及版本记录的分页查询端点。
 */
@Tag(name = "知识推送与版本", description = "知识元数据版本生成、Git 推送提交、ZIP 数据导出接口")
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    /**
     * 根据复核通过的任务创建知识版本记录
     *
     * @param taskId      任务 ID
     * @param versionNum  自定义版本号（如 v1.0.0）
     * @param confirmedBy 操作确认负责人用户名
     */
    @Operation(summary = "创建新知识版本")
    @PostMapping("/version")
    public ApiResponse<KnowledgeVersion> createVersion(
            @RequestParam Long taskId,
            @RequestParam String versionNum,
            @RequestParam(required = false, defaultValue = "Admin") String confirmedBy) {
        KnowledgeVersion version = knowledgeService.createVersion(taskId, versionNum, confirmedBy);
        return ApiResponse.success(version);
    }

    /**
     * 提交推送当前版本到目标 Git 代码库
     */
    @Operation(summary = "提交推送至 Git 代码库")
    @PostMapping("/{versionId}/push")
    public ApiResponse<Void> push(@PathVariable Long versionId) {
        knowledgeService.pushToGit(versionId);
        return ApiResponse.success();
    }

    /**
     * 导出当前版本的所有文档为 ZIP 格式的二进制数据流进行本地下载
     */
    @Operation(summary = "导出为 ZIP 包二进制流")
    @GetMapping("/{versionId}/export")
    public ResponseEntity<byte[]> exportZip(@PathVariable Long versionId) {
        byte[] bytes = knowledgeService.exportZip(versionId);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=code-insight-knowledge-" + versionId + ".zip");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    /**
     * 知识发布版本的分页列表查询
     */
    @Operation(summary = "知识版本分页查询")
    @GetMapping("/page")
    public ApiResponse<PageResult<KnowledgeVersion>> getPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long systemId) {
        Page<KnowledgeVersion> page = knowledgeService.listVersionsPage(current, size, systemId);
        PageResult<KnowledgeVersion> result = new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(), page.getRecords());
        return ApiResponse.success(result);
    }
}

