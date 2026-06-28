package com.company.codeinsight.modules.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.draft.entity.KnowledgeDraft;
import com.company.codeinsight.modules.draft.enums.DraftStatus;
import com.company.codeinsight.modules.draft.mapper.KnowledgeDraftMapper;
import com.company.codeinsight.modules.repository.entity.CodeRepository;
import com.company.codeinsight.modules.repository.service.CodeRepositoryService;
import com.company.codeinsight.modules.system.entity.SystemApplication;
import com.company.codeinsight.modules.system.service.SystemApplicationService;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.enums.TaskStatus;
import com.company.codeinsight.modules.task.mapper.DecompileTaskMapper;
import com.company.codeinsight.modules.task.service.DecompileTaskService;
import com.company.codeinsight.modules.task.service.TaskStateMachineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.company.codeinsight.modules.chunk.entity.CodeChunk;
import java.util.concurrent.CompletableFuture;

/**
 * 反编译/静态扫描任务管理服务实现类
 * 负责任务的创建、启动、重跑、终止，以及协调代码拉取、静态解析、切片提取、大模型调用和 Markdown 草稿生成的完整流水线工作流。
 */
@Slf4j
@Service
public class DecompileTaskServiceImpl extends ServiceImpl<DecompileTaskMapper, DecompileTask> implements DecompileTaskService {

    // 运行态任务的共享内存快照映射，避免事务提交延迟导致高频轮询读不到内存状态的竞争情况
    public static final java.util.Map<Long, DecompileTask> taskCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    private TaskStateMachineService stateMachineService;

    @Autowired
    private com.company.codeinsight.modules.scanner.service.CodeScannerService codeScannerService;

    @Autowired
    private com.company.codeinsight.modules.chunk.service.CodeChunkService codeChunkService;

    @Autowired
    private com.company.codeinsight.modules.ai.service.AiSummaryService aiSummaryService;

    @Autowired
    private com.company.codeinsight.modules.prompt.mapper.DecompilePromptMapper promptMapper;

    @Autowired
    private com.company.codeinsight.modules.model.mapper.AiModelMapper aiModelMapper;

    @Autowired
    private SystemApplicationService systemApplicationService;

    @Autowired
    private CodeRepositoryService codeRepositoryService;

    /**
     * 草稿主表映射：用于任务创建前置条件校验，
     * 扫描 ci_knowledge_draft 中仍处于非终态的草稿。
     */
    @Autowired
    private KnowledgeDraftMapper draftMapper;

    /**
     * 分页获取任务列表
     */
    @Override
    public Page<DecompileTask> listTasksPage(int current, int size, Long systemId, String status, String type, List<String> statuses) {
        Page<DecompileTask> page = new Page<>(current, size);
        LambdaQueryWrapper<DecompileTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(systemId != null, DecompileTask::getSystemId, systemId)
                // status（单值）与 statuses（多值）互斥：单值用 eq，多值用 in
                .eq(StringUtils.hasText(status), DecompileTask::getStatus, status)
                .in(statuses != null && !statuses.isEmpty(), DecompileTask::getStatus, statuses)
                .eq(StringUtils.hasText(type), DecompileTask::getType, type)
                .orderByDesc(DecompileTask::getCreatedAt);
        return this.page(page, queryWrapper);
    }

    /**
     * 各状态分组的固定映射，便于一处维护。
     * key 是分组标识（API 返回给前端），value 是该分组下包含的所有状态枚举名。
     */
    private static final Map<String, List<String>> TASK_STATUS_GROUPS = Map.of(
            "RUNNING",         List.of("PENDING", "PULLING_CODE", "PARSING_CODE", "SPLITTING_TASK", "AI_ANALYZING", "GENERATING_DOC", "PUSHING"),
            "PENDING_REVIEW",  List.of("PENDING_REVIEW", "REVIEWING"),
            "CONFIRMED",       List.of("CONFIRMED", "PUSHED"),
            "CLOSED",          List.of("FAILED", "CANCELLED", "ARCHIVED", "DRAFT")
    );

    @Override
    public Map<String, Long> countByStatusGroup(Long systemId) {
        Map<String, Long> result = new HashMap<>();
        // 初始化所有分组为 0
        for (String key : TASK_STATUS_GROUPS.keySet()) {
            result.put(key, 0L);
        }

        // 一次性 GROUP BY status 拉所有状态的数量（按 systemId 过滤）
        LambdaQueryWrapper<DecompileTask> qw = new LambdaQueryWrapper<>();
        if (systemId != null) {
            qw.eq(DecompileTask::getSystemId, systemId);
        }
        qw.select(DecompileTask::getStatus);
        List<DecompileTask> all = this.list(qw);

        long total = 0L;
        // 累加 ALL 与各分组
        for (DecompileTask t : all) {
            total += 1;
            for (Map.Entry<String, List<String>> entry : TASK_STATUS_GROUPS.entrySet()) {
                if (entry.getValue().contains(t.getStatus())) {
                    result.merge(entry.getKey(), 1L, Long::sum);
                }
            }
        }
        result.put("ALL", total);
        return result;
    }

    /**
     * 创建全量代码分析任务
     *
     * @param systemId     所属业务系统 ID
     * @param repositoryId 所选代码仓库 ID
     * @param promptVersion 提示词版本
     * @param modelName    大模型名称
     * @return 新增的任务记录对象
     */
    @Override
    @Transactional
    public DecompileTask createInitialTask(Long systemId, Long repositoryId, Integer promptVersion, String modelName) {
        // 验证系统和仓库的从属合法性
        validateTaskSource(systemId, repositoryId);
        // 业务前置条件：全局任意一个草稿仍处于非终态时禁止新建任务，
        // 避免在历史任务未完成时启动新的分析流水线导致草稿上下文撕裂。
        validateNoUnconfirmedDrafts();
        
        DecompileTask task = new DecompileTask();
        task.setSystemId(systemId);
        task.setRepositoryId(repositoryId);
        task.setPromptVersion(promptVersion);
        task.setStatus(TaskStatus.DRAFT.name());
        task.setType("INITIAL");
        task.setProgress(0);
        
        // 若没有手动指定大模型，则拉取系统默认配置的模型
        if (!org.springframework.util.StringUtils.hasText(modelName)) {
            com.company.codeinsight.modules.model.entity.AiModel defaultModel = aiModelMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.codeinsight.modules.model.entity.AiModel>()
                            .eq(com.company.codeinsight.modules.model.entity.AiModel::getIsDefault, "true")
                            .last("LIMIT 1")
            );
            if (defaultModel != null) {
                modelName = defaultModel.getIdentifier();
            }
        }
        task.setModelName(modelName);
        
        this.save(task);
        return task;
    }

    /**
     * 创建增量代码分析任务
     */
    @Override
    @Transactional
    public DecompileTask createIncrementalTask(Long systemId, Long repositoryId, Integer promptVersion, String modelName) {
        validateTaskSource(systemId, repositoryId);
        validateNoUnconfirmedDrafts();
        DecompileTask task = new DecompileTask();
        task.setSystemId(systemId);
        task.setRepositoryId(repositoryId);
        task.setPromptVersion(promptVersion);
        task.setStatus(TaskStatus.DRAFT.name());
        task.setType("INCREMENTAL");
        task.setProgress(0);
        
        if (!org.springframework.util.StringUtils.hasText(modelName)) {
            com.company.codeinsight.modules.model.entity.AiModel defaultModel = aiModelMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.codeinsight.modules.model.entity.AiModel>()
                            .eq(com.company.codeinsight.modules.model.entity.AiModel::getIsDefault, "true")
                            .last("LIMIT 1")
            );
            if (defaultModel != null) {
                modelName = defaultModel.getIdentifier();
            }
        }
        task.setModelName(modelName);
        
        this.save(task);
        return task;
    }

    /**
     * 数据源合规性拦截校验
     */
    private void validateTaskSource(Long systemId, Long repositoryId) {
        if (systemId == null || repositoryId == null) {
            throw new BusinessException("请选择系统和代码库");
        }

        SystemApplication system = systemApplicationService.getById(systemId);
        if (system == null) {
            throw new BusinessException("所选系统不存在");
        }
        if (!Objects.equals(system.getStatus(), 1)) {
            throw new BusinessException("所选系统已停用");
        }

        CodeRepository repository = codeRepositoryService.getById(repositoryId);
        if (repository == null) {
            throw new BusinessException("所选代码库不存在");
        }
        if (!Objects.equals(repository.getSystemId(), systemId)) {
            throw new BusinessException("所选代码库不属于当前系统");
        }
    }

    /**
     * 业务前置条件：全局任意一个草稿仍处于非终态时禁止新建任务。
     *
     * <p>非终态白名单与 {@link com.company.codeinsight.modules.draft.service.impl.DraftServiceImpl#findGlobalReadiness()}
     * 保持一致：DRAFT / EDITING 视为未完成。CONFIRMED / PUSHED / ARCHIVED 算"已消化"。
     * 自 v0.3 起 REJECTED 已从枚举移除，存量 REJECTED 由 schema.sql 末尾的迁移刷为 DRAFT，
     * 故本校验不再单独判 REJECTED。</p>
     *
     * <p>为什么放在 service 层而不是 controller：避免前端绕过向导直接 POST 时绕过校验；
     * 状态机层也无法拦截（状态机只管"任务已经创建之后"的流转）。</p>
     */
    private void validateNoUnconfirmedDrafts() {
        long blocking = draftMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDraft>()
                        .notIn(KnowledgeDraft::getStatus, java.util.List.of(
                                DraftStatus.CONFIRMED.name(),
                                DraftStatus.PUSHED.name(),
                                DraftStatus.ARCHIVED.name()
                        ))
        );
        if (blocking > 0) {
            throw new BusinessException("当前仍有 " + blocking +
                    " 个草稿未确认，无法新建任务。请前往「复核工作区」完成确认。");
        }
    }

    /**
     * 启动分析任务
     * 将任务置入缓存并扭转状态机为 PENDING，随后启动异步线程池执行拉取、解析、分片与分析。
     *
     * @param id 任务 ID
     */
    @Override
    public void startTask(Long id) {
        DecompileTask task = this.getById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        taskCache.put(task.getId(), task);
        stateMachineService.transitTo(task, TaskStatus.PENDING, null);

        // 运行异步管道线程
        runPipeline(task.getId());
    }

    /**
     * 强制终止分析任务
     */
    @Override
    @Transactional
    public void terminateTask(Long id) {
        DecompileTask task = this.getById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        stateMachineService.transitTo(task, TaskStatus.CANCELLED, "用户终止了任务");
    }

    /**
     * 重跑/重试任务
     */
    @Override
    public void retryTask(Long id) {
        DecompileTask task = this.getById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        taskCache.put(task.getId(), task);
        stateMachineService.transitTo(task, TaskStatus.PENDING, null);
        runPipeline(task.getId());
    }

    /**
     * 异步流水线核心控制器
     * 将代码扫描、切片、AI分析和归纳归整串联在一起的无阻塞后台流水线。
     */
    private void runPipeline(Long taskId) {
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 进入代码拉取阶段 (PULLING_CODE)
                stateMachineService.transitTo(taskId, TaskStatus.PULLING_CODE, null);
                DecompileTask task = this.getById(taskId);
                if (task == null) {
                    task = taskCache.get(taskId);
                }
                if (task == null) {
                    throw new BusinessException("任务不存在, ID: " + taskId);
                }
                
                // 执行 Git 拉取/本地备份
                File projectDir = codeScannerService.pullAndScan(taskId, task.getRepositoryId());

                // 2. 进入代码静态分析解析阶段 (PARSING_CODE)
                stateMachineService.transitTo(taskId, TaskStatus.PARSING_CODE, null);
                List<com.company.codeinsight.modules.scanner.entity.CodeFileSnapshot> snapshots = codeScannerService.getSnapshotsByTaskId(taskId);

                // 3. 进入分片切割提取阶段 (SPLITTING_TASK)
                stateMachineService.transitTo(taskId, TaskStatus.SPLITTING_TASK, null);
                codeChunkService.chunkAndEstimate(taskId, snapshots);

                // 4. 进入大模型 AI 归纳分析阶段 (AI_ANALYZING)
                stateMachineService.transitTo(taskId, TaskStatus.AI_ANALYZING, null);
                List<CodeChunk> chunks = codeChunkService.getChunksByTaskId(taskId);

                // 确定大模型分析时所用提示词的正文
                String promptContent = "你是一个代码归纳助手，请对以下代码的业务功能进行高度归纳。";
                if (task.getPromptVersion() != null) {
                    com.company.codeinsight.modules.prompt.entity.DecompilePrompt prompt = promptMapper.selectOne(
                            new LambdaQueryWrapper<com.company.codeinsight.modules.prompt.entity.DecompilePrompt>()
                                     .eq(com.company.codeinsight.modules.prompt.entity.DecompilePrompt::getVersion, task.getPromptVersion())
                                     .eq(com.company.codeinsight.modules.prompt.entity.DecompilePrompt::getStatus, 1)
                                     .last("LIMIT 1")
                    );
                    if (prompt != null) {
                        promptContent = prompt.getContent();
                    }
                } else {
                    // 若无版本指定，则选取系统默认激活的主提示词模板
                    com.company.codeinsight.modules.prompt.entity.DecompilePrompt defaultPrompt = promptMapper.selectOne(
                            new LambdaQueryWrapper<com.company.codeinsight.modules.prompt.entity.DecompilePrompt>()
                                    .eq(com.company.codeinsight.modules.prompt.entity.DecompilePrompt::getIsDefault, 1)
                                    .eq(com.company.codeinsight.modules.prompt.entity.DecompilePrompt::getStatus, 1)
                                    .last("LIMIT 1")
                    );
                    if (defaultPrompt != null) {
                        promptContent = defaultPrompt.getContent();
                        task.setPromptVersion(defaultPrompt.getVersion());
                        this.updateById(task);
                    }
                }

                // 5. 进入草稿汇总归档阶段 (GENERATING_DOC)
                stateMachineService.transitTo(taskId, TaskStatus.GENERATING_DOC, null);
                aiSummaryService.generateDraftDocument(taskId, chunks, promptContent);

                // 6. 成功流转为终态：等待人工复核 (PENDING_REVIEW)
                stateMachineService.transitTo(taskId, TaskStatus.PENDING_REVIEW, null);

            } catch (Exception e) {
                log.error("Pipeline run failed for task " + taskId, e);
                try {
                    // 发生任何步骤故障时，流转状态为 FAILED，并登记错误日志原因
                    stateMachineService.transitTo(taskId, TaskStatus.FAILED, e.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to transit failed status", ex);
                }
            } finally {
                // 清理临时缓存，释放共享内存
                taskCache.remove(taskId);
            }
        });
    }
}

