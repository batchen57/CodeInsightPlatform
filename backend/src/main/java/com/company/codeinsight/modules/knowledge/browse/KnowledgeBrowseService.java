package com.company.codeinsight.modules.knowledge.browse;

import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseContentRequest;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseItem;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseQuery;

import java.util.List;

/**
 * 知识查看服务：按系统维度聚合展示知识文档（草稿）+ 索引/清单文件，提供简单 / 复杂搜索。
 * <p>只读，不修改任何文件 / DB 行。</p>
 */
public interface KnowledgeBrowseService {

    /**
     * 按条件列出系统下的所有知识文档 / 索引文件 / 清单文件
     */
    List<KnowledgeBrowseItem> list(KnowledgeBrowseQuery query);

    /**
     * 读取单条目的原始文本内容（Markdown / YAML / JSON 字符串）。
     * <p>大小限制与路径校验由 {@link KnowledgeBrowseSource} 实现层负责。</p>
     */
    String readContent(KnowledgeBrowseContentRequest req);
}