package com.company.codeinsight.modules.knowledge.browse;

import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseContentRequest;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseItem;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseQuery;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseTreeQuery;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseTreeResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识查看聚合浏览接口
 * <p>URL 前缀 {@code /api/knowledge/browse}：</p>
 * <ul>
 *   <li>{@code GET /} 列出系统下所有知识文档 + 索引/清单文件（按 query 过滤）</li>
 *   <li>{@code GET /content} 读取单条目的原始文本</li>
 * </ul>
 */
@Tag(name = "知识查看", description = "按系统聚合浏览知识文档 / 索引 / 清单文件（只读）")
@RestController
@RequestMapping("/knowledge/browse")
public class KnowledgeBrowseController {

    @Autowired
    private KnowledgeBrowseService knowledgeBrowseService;

    @Operation(summary = "分页列出知识文档 / 索引 / 清单文件（支持跨系统）")
    @GetMapping
    public ApiResponse<PageResult<KnowledgeBrowseItem>> list(@ModelAttribute KnowledgeBrowseQuery query) {
        return ApiResponse.success(knowledgeBrowseService.listPage(query));
    }

    @Operation(summary = "树形模式：按模块层级展示功能与关联知识文档")
    @GetMapping("/tree")
    public ApiResponse<KnowledgeBrowseTreeResult> tree(@ModelAttribute KnowledgeBrowseTreeQuery query) {
        return ApiResponse.success(knowledgeBrowseService.buildTree(query));
    }

    @Operation(summary = "读取单条目的原始文本内容")
    @GetMapping("/content")
    public ApiResponse<String> readContent(@ModelAttribute KnowledgeBrowseContentRequest req) {
        return ApiResponse.success(knowledgeBrowseService.readContent(req));
    }
}