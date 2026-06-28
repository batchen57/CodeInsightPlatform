package com.company.codeinsight.modules.task.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.entrypoint.model.EntryPointConfig;
import com.company.codeinsight.modules.task.entity.DecompileTask;

/**
 * 反编译及扫描分析任务管理服务接口
 * 负责定义初始化与增量任务创建、任务分页拉取、任务启动/终止/重试等流水线控制逻辑。
 */
public interface DecompileTaskService extends IService<DecompileTask> {

    /**
     * 分页、条件查询分析任务列表
     */
    Page<DecompileTask> listTasksPage(int current, int size, Long systemId, String status, String type);

    /**
     * 创建一个全新的全量初始化分析任务，清空历史指纹快照
     *
     * @param systemId              关联系统 ID
     * @param repositoryId          关联代码库 ID
     * @param modularizePromptId    模块提取提示词 ID（按 ci_prompt 主键），可为 null 走默认
     * @param documentPromptId      文档生成提示词 ID（按 ci_prompt 主键），可为 null 走默认
     * @param modelName             选定大模型标识
     * @param entryScanConfig       入口扫描配置（可为 null 表示走默认 Controller/JOB/MQ 兜底）
     * @param requireHierarchyReview 是否启用模块层级调试断点；null 时按默认 TRUE 处理
     * @return 刚创建的 DecompileTask 对象
     */
    DecompileTask createInitialTask(Long systemId, Long repositoryId,
                                    Long modularizePromptId, Long documentPromptId,
                                    String modelName,
                                    EntryPointConfig entryScanConfig, Boolean requireHierarchyReview);

    /**
     * 创建一个全新的增量更新分析任务，根据 Git 变更选择性扫描分析
     */
    DecompileTask createIncrementalTask(Long systemId, Long repositoryId,
                                        Long modularizePromptId, Long documentPromptId,
                                        String modelName,
                                        EntryPointConfig entryScanConfig, Boolean requireHierarchyReview);

    /**
     * 触发异步执行引擎以启动该分析任务
     *
     * @param id 任务 ID
     */
    void startTask(Long id);

    /**
     * 强行中止或取消正在进行中的分析任务
     *
     * @param id 任务 ID
     */
    void terminateTask(Long id);

    /**
     * 重试运行失败的扫描任务
     *
     * @param id 任务 ID
     */
    void retryTask(Long id);

    /**
     * 在模块层级人工复核断点 (MODULE_HIERARCHY_REVIEW) 处恢复流水线
     * <p>
     * 前置条件：任务当前处于 MODULE_HIERARCHY_REVIEW 状态；
     * 行为：校验状态 → 流转到 GENERATING_DOC → 调用 AiSummaryService.generateDraftDocument → 流转到 PENDING_REVIEW。
     * 与 runPipeline 中 GENERATING_DOC 阶段保持一致逻辑。
     */
    void resumeAfterHierarchyReview(Long id);
}

