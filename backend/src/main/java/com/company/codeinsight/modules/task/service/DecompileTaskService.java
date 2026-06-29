package com.company.codeinsight.modules.task.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.entrypoint.model.EntryPointConfig;
import com.company.codeinsight.modules.task.entity.DecompileTask;

import java.util.List;
import java.util.Map;

/**
 * 反编译及扫描分析任务管理服务接口
 * 负责定义初始化与增量任务创建、任务分页拉取、任务启动/终止/重试等流水线控制逻辑。
 */
public interface DecompileTaskService extends IService<DecompileTask> {

    /**
     * 分页、条件查询分析任务列表
     *
     * @param statuses        多状态过滤（如 chip 分组过滤时使用），与 status 单值互斥
     * @param scheduleId      触发该任务的 schedule 配置 ID（仅查询由指定定时任务触发的实例时使用），与 triggerSource 互斥
     * @param triggerSource   触发来源：MANUAL / SCHEDULED；与 scheduleId 互斥
     * @param keyword         简单搜索：纯数字按 id 精确匹配，否则按 model_name LIKE '%keyword%'
     * @param modelName       精准搜索：模型名精确匹配
     * @param createdAtStart  精准搜索：创建时间下界（ISO timestamp）
     * @param createdAtEnd    精准搜索：创建时间上界（ISO timestamp）
     */
    Page<DecompileTask> listTasksPage(int current, int size, Long systemId, String status, String type,
                                      List<String> statuses, Long scheduleId, String triggerSource,
                                      String keyword, String modelName,
                                      String createdAtStart, String createdAtEnd);

    /**
     * 按状态分组统计任务数量，供任务中心顶部 chips 角标使用。
     * <p>返回 5 个固定 key：</p>
     * <ul>
     *   <li>ALL：所有任务总数</li>
     *   <li>RUNNING：进行中（PENDING / PULLING_CODE / PARSING_CODE / SPLITTING_TASK / AI_ANALYZING / GENERATING_DOC / PUSHING）</li>
     *   <li>PENDING_REVIEW：待复核 + 复核中（PENDING_REVIEW / REVIEWING）</li>
     *   <li>CONFIRMED：已确认 + 推送中 + 已推送（CONFIRMED / PUSHED）</li>
     *   <li>CLOSED：已终止（FAILED / CANCELLED / ARCHIVED / DRAFT）</li>
     * </ul>
     *
     * @param systemId 可选系统过滤
     * @return 各分组任务数
     */
    Map<String, Long> countByStatusGroup(Long systemId);

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
     * 创建一个全量任务（含知识入口复核断点开关）。
     * <p>默认 {@code requireEntrypointReview=true}；调用方传 {@code false} 可跳过 ENTRYPOINT_REVIEW 断点。</p>
     */
    DecompileTask createInitialTask(Long systemId, Long repositoryId,
                                    Long modularizePromptId, Long documentPromptId,
                                    String modelName,
                                    EntryPointConfig entryScanConfig,
                                    Boolean requireHierarchyReview, Boolean requireEntrypointReview);

    /**
     * 创建一个全新的增量更新分析任务，根据 Git 变更选择性扫描分析
     */
    DecompileTask createIncrementalTask(Long systemId, Long repositoryId,
                                        Long modularizePromptId, Long documentPromptId,
                                        String modelName,
                                        EntryPointConfig entryScanConfig, Boolean requireHierarchyReview);

    /**
     * 创建一个增量任务（含知识入口复核断点开关）。
     * <p>默认 {@code requireEntrypointReview=true}；调用方传 {@code false} 可跳过 ENTRYPOINT_REVIEW 断点。</p>
     */
    DecompileTask createIncrementalTask(Long systemId, Long repositoryId,
                                        Long modularizePromptId, Long documentPromptId,
                                        String modelName,
                                        EntryPointConfig entryScanConfig,
                                        Boolean requireHierarchyReview, Boolean requireEntrypointReview);

    /**
     * 创建一个全量任务，并打上触发来源标签。
     *
     * <p>主要用于定时任务调度：每次 cron tick 会生成一条 ci_task 记录，
     * 通过 {@code triggerSource=SCHEDULED} + {@code scheduleId} 把该任务与具体的调度配置关联起来，
     * 便于前端在任务列表做来源筛选与回链到调度详情。</p>
     *
     * @param triggerSource 触发来源（MANUAL / SCHEDULED）；为 null 时按 MANUAL 处理
     * @param scheduleId    调度配置 ID（仅 SCHEDULED 时非空）
     */
    DecompileTask createInitialTask(Long systemId, Long repositoryId,
                                    Long modularizePromptId, Long documentPromptId,
                                    String modelName,
                                    EntryPointConfig entryScanConfig, Boolean requireHierarchyReview,
                                    String triggerSource, Long scheduleId);

    /**
     * 创建一个全量任务（含 ENTRYPOINT_REVIEW 断点开关 + 触发来源标签）
     */
    DecompileTask createInitialTask(Long systemId, Long repositoryId,
                                    Long modularizePromptId, Long documentPromptId,
                                    String modelName,
                                    EntryPointConfig entryScanConfig,
                                    Boolean requireHierarchyReview, Boolean requireEntrypointReview,
                                    String triggerSource, Long scheduleId);

    /**
     * 创建一个增量任务，并打上触发来源标签（同上）。
     */
    DecompileTask createIncrementalTask(Long systemId, Long repositoryId,
                                        Long modularizePromptId, Long documentPromptId,
                                        String modelName,
                                        EntryPointConfig entryScanConfig, Boolean requireHierarchyReview,
                                        String triggerSource, Long scheduleId);

    /**
     * 创建一个增量任务（含 ENTRYPOINT_REVIEW 断点开关 + 触发来源标签）
     */
    DecompileTask createIncrementalTask(Long systemId, Long repositoryId,
                                        Long modularizePromptId, Long documentPromptId,
                                        String modelName,
                                        EntryPointConfig entryScanConfig,
                                        Boolean requireHierarchyReview, Boolean requireEntrypointReview,
                                        String triggerSource, Long scheduleId);

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

    /**
     * 在知识入口人工复核断点 (ENTRYPOINT_REVIEW) 处恢复流水线。
     * <p>前置条件：任务当前处于 ENTRYPOINT_REVIEW 状态；
     * 行为：校验状态 → 读取流水线上下文 → 流转到 AI_ANALYZING → 模块层级（按 requireHierarchyReview 选择 GENERATING_DOC 或 MODULE_HIERARCHY_REVIEW）。
     * 与 runPipeline 中"跳过 ENTRYPOINT_REVIEW 断点"分支共享同一段尾代码（continueAfterEntrypointReview）。</p>
     */
    void resumeAfterEntrypointReview(Long id);

    /**
     * 在知识入口人工复核断点 (ENTRYPOINT_REVIEW) 处驳回任务，终止流水线。
     * <p>仅当任务处于 ENTRYPOINT_REVIEW 时可调用；流转到 CANCELLED（不入任何知识资产）。</p>
     *
     * @param id     任务 ID
     * @param reason 驳回理由（写进 task.error_reason；可空，默认 "用户在知识入口复核驳回"）
     */
    void rejectEntrypointReview(Long id, String reason);

    /**
     * 异步触发任务流水线（被 {@link com.company.codeinsight.modules.task.service.TaskQueueDispatcher} 调用）。
     * <p>之前是 private 方法；队列改造后暴露为 public，供 dispatcher 使用。</p>
     */
    void runPipeline(Long taskId);

    /**
     * 取消队列中的 PENDING 任务（PENDING → CANCELLED，无需终止线程）。
     */
    void cancelQueuedTask(Long id);

    /**
     * 调整任务优先级 0-100（仅 PENDING 可调）。
     */
    void adjustPriority(Long id, Integer newPriority);

    /**
     * 列出队列中的 PENDING 任务，按 priority DESC + created_at ASC。
     */
    Page<DecompileTask> listQueuedTasks(int current, int size, Long systemId);

    /**
     * 队列总览：{total, avgWaitSeconds}。
     */
    Map<String, Object> getQueueSummary();
}

