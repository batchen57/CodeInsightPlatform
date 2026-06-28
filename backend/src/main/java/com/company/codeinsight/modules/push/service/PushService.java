package com.company.codeinsight.modules.push.service;

import java.util.List;

import com.company.codeinsight.modules.push.entity.PushTask;
import com.company.codeinsight.modules.push.enums.PushMethod;

/**
 * 知识推送服务接口
 *
 * 负责推送任务入队到 Redis 队列、从队列中取出任务并分发给对应策略执行、查询推送历史。
 */
public interface PushService {

    /**
     * 将推送任务加入 Redis 队列，异步执行。
     * 入队前会进行版本状态校验和草稿内容校验。
     *
     * @param versionId 知识版本 ID
     * @param method    推送方式（GIT / S3）
     */
    void enqueuePush(Long versionId, PushMethod method);

    /**
     * 从 Redis 队列中取出一个任务并执行。
     * 由 PushTaskScheduler 定时调用。
     * 如果队列为空则立即返回（不阻塞）。
     */
    void processNextTask();

    /**
     * 查询指定版本关联的所有推送任务记录
     *
     * @param versionId 知识版本 ID
     * @return 推送任务列表（按创建时间降序）
     */
    List<PushTask> listTasksByVersion(Long versionId);
}
