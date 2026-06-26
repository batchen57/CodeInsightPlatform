package com.company.codeinsight.modules.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.modules.knowledge.entity.KnowledgeVersion;

public interface KnowledgeService {

    /**
     * 生成知识版本并创建 /docs/code-insight 本地目录结构
     */
    KnowledgeVersion createVersion(Long taskId, String versionNum, String confirmedBy);

    /**
     * 推送到 Git 仓库
     */
    void pushToGit(Long versionId);

    /**
     * 导出为 ZIP 压缩包
     */
    byte[] exportZip(Long versionId);

    /**
     * 分页查询知识版本记录
     */
    Page<KnowledgeVersion> listVersionsPage(int current, int size, Long systemId);
}
