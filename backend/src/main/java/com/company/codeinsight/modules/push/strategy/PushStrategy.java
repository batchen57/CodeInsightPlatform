package com.company.codeinsight.modules.push.strategy;

import com.company.codeinsight.modules.knowledge.entity.KnowledgeVersion;
import com.company.codeinsight.modules.push.entity.PushTask;
import com.company.codeinsight.modules.push.enums.PushMethod;

/**
 * 推送策略接口
 * 每种推送方式（Git / S3）对应一个策略实现，由调度器统一调用。
 */
public interface PushStrategy {

    /**
     * 执行具体的推送操作
     *
     * @param version 知识版本（包含 taskId、repositoryId 等上下文）
     * @param task    推送任务记录（用于记录 targetInfo 等审计信息）
     * @return 推送结果的标识符：Git 返回 commit SHA，S3 返回 object key
     * @throws com.company.codeinsight.common.exception.BusinessException 推送失败时抛出
     */
    String execute(KnowledgeVersion version, PushTask task);

    /**
     * 声明本策略支持的推送方式
     *
     * @return 对应的 PushMethod 枚举值
     */
    PushMethod getMethod();
}
