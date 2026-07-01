package com.company.codeinsight.modules.knowledge.browse;

import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseContentRequest;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseItem;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseQuery;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseTreeQuery;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseTreeResult;

import java.util.List;

/**
 * 知识查看服务：按系统维度聚合展示知识文档（草稿）+ 索引/清单文件，提供简单 / 复杂搜索。
 * <p>只读，不修改任何文件 / DB 行。</p>
 */
public interface KnowledgeBrowseService {

    /**
     * 按条件分页列出知识文档 / 索引 / 清单文件（systemId 可选，表示跨系统）
     */
    PageResult<KnowledgeBrowseItem> listPage(KnowledgeBrowseQuery query);

    /**
     * 树形模式：按模块层级展示功能叶子并关联草稿
     */
    KnowledgeBrowseTreeResult buildTree(KnowledgeBrowseTreeQuery query);

    /**
     * 读取单条目的原始文本内容（Markdown / YAML / JSON 字符串）。
     * <p>大小限制与路径校验由 {@link KnowledgeBrowseSource} 实现层负责。</p>
     */
    String readContent(KnowledgeBrowseContentRequest req);
}