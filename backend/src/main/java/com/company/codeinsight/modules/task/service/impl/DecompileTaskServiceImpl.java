package com.company.codeinsight.modules.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.ai.mapper.AiCallRecordMapper;
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
import java.util.List;
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

    @Autowired
    private com.company.codeinsight.modules.callchain.service.MethodCallService methodCallService;

    @Autowired
    private com.company.codeinsight.modules.hierarchy.service.ModuleHierarchyService moduleHierarchyService;

    @Autowired
    private com.company.codeinsight.modules.task.service.TaskExecutionLogger execLog;

    @Autowired
    private AiCallRecordMapper aiCallRecordMapper;

    /**
     * 分页获取任务列表
     */
    @Override
    public Page<DecompileTask> listTasksPage(int current, int size, Long systemId, String status, String type) {
        Page<DecompileTask> page = new Page<>(current, size);
        LambdaQueryWrapper<DecompileTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(systemId != null, DecompileTask::getSystemId, systemId)
                .eq(StringUtils.hasText(status), DecompileTask::getStatus, status)
                .eq(StringUtils.hasText(type), DecompileTask::getType, type)
                .orderByDesc(DecompileTask::getCreatedAt);
        return this.page(page, queryWrapper);
    }

    /**
     * 创建全量代码分析任务
     *
     * @param systemId            所属业务系统 ID
     * @param repositoryId        所选代码仓库 ID
     * @param modularizePromptId  模块提取提示词 ID（ci_prompt 主键），可空走默认
     * @param documentPromptId    文档生成提示词 ID（ci_prompt 主键），可空走默认
     * @param modelName           大模型名称
     * @return 新增的任务记录对象
     */
    @Override
    @Transactional
    public DecompileTask createInitialTask(Long systemId, Long repositoryId,
                                           Long modularizePromptId, Long documentPromptId,
                                           String modelName,
                                           com.company.codeinsight.modules.entrypoint.model.EntryPointConfig entryScanConfig,
                                           Boolean requireHierarchyReview) {
        // 验证系统和仓库的从属合法性
        validateTaskSource(systemId, repositoryId);

        DecompileTask task = new DecompileTask();
        task.setSystemId(systemId);
        task.setRepositoryId(repositoryId);
        applyPromptIds(task, modularizePromptId, documentPromptId);
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
        task.setEntryScanConfig(com.company.codeinsight.modules.entrypoint.model.EntryPointConfigCodec.encode(entryScanConfig));
        // 默认开启模块层级调试断点，调用方显式传 false 才跳过
        task.setRequireHierarchyReview(requireHierarchyReview == null ? Boolean.TRUE : requireHierarchyReview);

        this.save(task);
        return task;
    }

    /**
     * 创建增量代码分析任务
     */
    @Override
    @Transactional
    public DecompileTask createIncrementalTask(Long systemId, Long repositoryId,
                                               Long modularizePromptId, Long documentPromptId,
                                               String modelName,
                                               com.company.codeinsight.modules.entrypoint.model.EntryPointConfig entryScanConfig,
                                               Boolean requireHierarchyReview) {
        validateTaskSource(systemId, repositoryId);
        DecompileTask task = new DecompileTask();
        task.setSystemId(systemId);
        task.setRepositoryId(repositoryId);
        applyPromptIds(task, modularizePromptId, documentPromptId);
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
        task.setEntryScanConfig(com.company.codeinsight.modules.entrypoint.model.EntryPointConfigCodec.encode(entryScanConfig));
        task.setRequireHierarchyReview(requireHierarchyReview == null ? Boolean.TRUE : requireHierarchyReview);

        this.save(task);
        return task;
    }

    /**
     * 把传入的两类提示词 id 写入 task（先取调用方指定 id，未指定则取同类型默认提示词的 id）。
     */
    private void applyPromptIds(DecompileTask task, Long modularizePromptId, Long documentPromptId) {
        task.setModularizePromptId(modularizePromptId != null
                ? modularizePromptId
                : findDefaultPromptId(com.company.codeinsight.modules.prompt.entity.DecompilePrompt.TYPE_MODULARIZE));
        task.setDocumentPromptId(documentPromptId != null
                ? documentPromptId
                : findDefaultPromptId(com.company.codeinsight.modules.prompt.entity.DecompilePrompt.TYPE_DOCUMENT_GENERATION));
    }

    private Long findDefaultPromptId(String promptType) {
        if (!org.springframework.util.StringUtils.hasText(promptType)) return null;
        com.company.codeinsight.modules.prompt.entity.DecompilePrompt prompt = promptMapper.selectOne(
                new LambdaQueryWrapper<com.company.codeinsight.modules.prompt.entity.DecompilePrompt>()
                        .eq(com.company.codeinsight.modules.prompt.entity.DecompilePrompt::getPromptType, promptType)
                        .eq(com.company.codeinsight.modules.prompt.entity.DecompilePrompt::getIsDefault, 1)
                        .eq(com.company.codeinsight.modules.prompt.entity.DecompilePrompt::getStatus, 1)
                        .last("LIMIT 1")
        );
        return prompt != null ? prompt.getId() : null;
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
     * 在模块层级人工复核断点 (MODULE_HIERARCHY_REVIEW) 处恢复流水线
     * <p>
     * 仅当任务处于 MODULE_HIERARCHY_REVIEW 时可调用；流转到 GENERATING_DOC → 生成草稿 → PENDING_REVIEW。
     * 异步执行，与 runPipeline 中对应阶段保持一致逻辑。
     */
    @Override
    public void resumeAfterHierarchyReview(Long id) {
        DecompileTask task = this.getById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        TaskStatus current = TaskStatus.valueOf(task.getStatus());
        if (current != TaskStatus.MODULE_HIERARCHY_REVIEW) {
            throw new BusinessException("仅在模块层级复核状态下可恢复，当前状态: " + current);
        }

        taskCache.put(task.getId(), task);
        CompletableFuture.runAsync(() -> {
            try {
                String promptContent = resolvePromptContent(task);
                List<CodeChunk> chunks = codeChunkService.getChunksByTaskId(id);

                // 进入草稿汇总归档阶段 (GENERATING_DOC)
                stateMachineService.transitTo(id, TaskStatus.GENERATING_DOC, null);
                aiSummaryService.generateDraftDocument(id, chunks, promptContent);

                // 流转为终态：等待人工复核 (PENDING_REVIEW)
                stateMachineService.transitTo(id, TaskStatus.PENDING_REVIEW, null);
            } catch (Exception e) {
                log.error("Resume after hierarchy review failed for task " + id, e);
                try {
                    stateMachineService.transitTo(id, TaskStatus.FAILED, e.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to transit failed status", ex);
                }
            } finally {
                taskCache.remove(id);
            }
        });
    }

    /**
     * 按 task 记录的 documentPromptId（或 DOCUMENT_GENERATION 类型的默认提示词）解析草稿生成时所用的提示词正文。
     */
    private String resolvePromptContent(DecompileTask task) {
        final String fallback = "你是一个代码归纳助手，请对以下代码的业务功能进行高度归纳。";
        if (task.getDocumentPromptId() != null) {
            com.company.codeinsight.modules.prompt.entity.DecompilePrompt prompt = promptMapper.selectById(task.getDocumentPromptId());
            if (prompt != null && Integer.valueOf(1).equals(prompt.getStatus())) return prompt.getContent();
        }
        // 兜底：DOCUMENT_GENERATION 默认
        com.company.codeinsight.modules.prompt.entity.DecompilePrompt defaultPrompt = promptMapper.selectOne(
                new LambdaQueryWrapper<com.company.codeinsight.modules.prompt.entity.DecompilePrompt>()
                        .eq(com.company.codeinsight.modules.prompt.entity.DecompilePrompt::getPromptType, com.company.codeinsight.modules.prompt.entity.DecompilePrompt.TYPE_DOCUMENT_GENERATION)
                        .eq(com.company.codeinsight.modules.prompt.entity.DecompilePrompt::getIsDefault, 1)
                        .eq(com.company.codeinsight.modules.prompt.entity.DecompilePrompt::getStatus, 1)
                        .last("LIMIT 1")
        );
        if (defaultPrompt != null) {
            task.setDocumentPromptId(defaultPrompt.getId());
            this.updateById(task);
            return defaultPrompt.getContent();
        }
        return fallback;
    }

    /**
     * 异步流水线核心控制器
     * 将代码扫描、切片、AI分析和归纳归整串联在一起的无阻塞后台流水线。
     */
    private void runPipeline(Long taskId) {
        CompletableFuture.runAsync(() -> {
            long t0 = System.currentTimeMillis();
            try {
                execLog.log(taskId, "══════ 流水线启动 taskId=" + taskId + " ══════");

                // 1. PULLING_CODE
                stateMachineService.transitTo(taskId, TaskStatus.PULLING_CODE, null);
                DecompileTask task = this.getById(taskId);
                if (task == null) task = taskCache.get(taskId);
                if (task == null) throw new BusinessException("任务不存在, ID: " + taskId);

                execLog.log(taskId, ">>> PULLING_CODE — 拉取代码");
                execLog.log(taskId, "  model          = " + (task.getModelName() != null ? task.getModelName() : "(default)"));
                execLog.log(taskId, "  hierarchyReview= " + task.getRequireHierarchyReview());
                execLog.log(taskId, "  type           = " + task.getType());
                execLog.log(taskId, "  repositoryId   = " + task.getRepositoryId());

                long t1 = System.currentTimeMillis();
                com.company.codeinsight.modules.scanner.model.ScanResult scanResult =
                        codeScannerService.pullAndScan(taskId, task.getRepositoryId(), task.getType());
                File projectDir = scanResult.getProjectDir();
                com.company.codeinsight.modules.scanner.model.IncrementalContext incrementalCtx = scanResult.getIncrementalContext();
                execLog.log(taskId, "  projectDir     = " + projectDir.getAbsolutePath());
                execLog.log(taskId, "  scanMode       = " + (incrementalCtx.isIncremental() ? "INCREMENTAL" : "INITIAL"));
                if (incrementalCtx.isIncremental()) {
                    execLog.log(taskId, "  changed files  = " + incrementalCtx.getChangedPaths().size()
                            + ", deleted files = " + incrementalCtx.getDeletedPaths().size());
                }
                execLog.log(taskId, "  耗时 " + (System.currentTimeMillis() - t1) + "ms");

                // 2. PARSING_CODE
                stateMachineService.transitTo(taskId, TaskStatus.PARSING_CODE, null);
                execLog.log(taskId, ">>> PARSING_CODE — AST 静态解析 + 调用链落表");
                t1 = System.currentTimeMillis();

                List<com.company.codeinsight.modules.scanner.entity.CodeFileSnapshot> snapshots = codeScannerService.getSnapshotsByTaskId(taskId);
                execLog.log(taskId, "  文件快照数    = " + snapshots.size());

                int callCount = methodCallService.persistAstForTask(taskId, projectDir, incrementalCtx);
                execLog.log(taskId, "  方法调用链数   = " + callCount);
                execLog.log(taskId, "  耗时 " + (System.currentTimeMillis() - t1) + "ms");

                // 3. SPLITTING_TASK
                stateMachineService.transitTo(taskId, TaskStatus.SPLITTING_TASK, null);
                execLog.log(taskId, ">>> SPLITTING_TASK — 代码切片");
                t1 = System.currentTimeMillis();
                codeChunkService.chunkAndEstimate(taskId, snapshots, incrementalCtx);

                List<CodeChunk> allChunks = codeChunkService.getChunksByTaskId(taskId);
                long fileCount = allChunks.stream().filter(c -> "FILE".equals(c.getChunkType())).count();
                long clsCount = allChunks.stream().filter(c -> "CLASS".equals(c.getChunkType())).count();
                long mtdCount = allChunks.stream().filter(c -> "METHOD".equals(c.getChunkType())).count();
                execLog.log(taskId, "  切片总数       = " + allChunks.size() + " (FILE=" + fileCount + " CLASS=" + clsCount + " METHOD=" + mtdCount + ")");
                execLog.log(taskId, "  耗时 " + (System.currentTimeMillis() - t1) + "ms");

                // 4. AI_ANALYZING → MODULE_HIERARCHY
                stateMachineService.transitTo(taskId, TaskStatus.AI_ANALYZING, null);
                execLog.log(taskId, ">>> AI_ANALYZING — AI 归纳");
                execLog.log(taskId, "  aiMock=" + aiSummaryService.isAiMock() + " | model=" + (task.getModelName() != null ? task.getModelName() : "(default)"));
                long aiT0 = System.currentTimeMillis();
                stateMachineService.transitTo(taskId, TaskStatus.MODULE_HIERARCHY, null);
                execLog.log(taskId, ">>> MODULE_HIERARCHY — AI 提炼模块层级");
                t1 = System.currentTimeMillis();
                com.company.codeinsight.modules.hierarchy.model.ModuleHierarchy hierarchy =
                        moduleHierarchyService.buildAndPersist(taskId, projectDir, incrementalCtx);
                int modCount = hierarchy.getModules() != null ? hierarchy.getModules().size() : 0;
                execLog.log(taskId, "  模块数         = " + modCount);
                execLog.log(taskId, "  耗时 " + (System.currentTimeMillis() - t1) + "ms");

                List<CodeChunk> chunks = codeChunkService.getChunksByTaskId(taskId);

                // 5. 调试断点 / GENERATING_DOC
                if (!Boolean.TRUE.equals(task.getRequireHierarchyReview())) {
                    execLog.log(taskId, ">>> GENERATING_DOC — 生成文档");
                    t1 = System.currentTimeMillis();
                    stateMachineService.transitTo(taskId, TaskStatus.GENERATING_DOC, null);
                    aiSummaryService.generateDraftDocument(taskId, chunks, resolvePromptContent(task), incrementalCtx);
                    stateMachineService.transitTo(taskId, TaskStatus.PENDING_REVIEW, null);
                    execLog.log(taskId, "  耗时 " + (System.currentTimeMillis() - t1) + "ms");
                    // AI 阶段终态汇总：从 ci_ai_call_record 统计本次任务的成功/失败次数
                    long aiOk = aiCallRecordMapper.selectCount(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.codeinsight.modules.ai.entity.AiCallRecord>()
                                    .eq(com.company.codeinsight.modules.ai.entity.AiCallRecord::getTaskId, taskId)
                                    .eq(com.company.codeinsight.modules.ai.entity.AiCallRecord::getIsSuccess, 1));
                    long aiFail = aiCallRecordMapper.selectCount(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.codeinsight.modules.ai.entity.AiCallRecord>()
                                    .eq(com.company.codeinsight.modules.ai.entity.AiCallRecord::getTaskId, taskId)
                                    .eq(com.company.codeinsight.modules.ai.entity.AiCallRecord::getIsSuccess, 0));
                    execLog.log(taskId, "<<< AI_ANALYZING 完成 (成功 " + aiOk + " 失败 " + aiFail + ", 耗时 " + (System.currentTimeMillis() - aiT0) + "ms)");
                    execLog.log(taskId, "<<< 流水线完成 → PENDING_REVIEW (总耗时 " + (System.currentTimeMillis() - t0) + "ms)");
                } else {
                    stateMachineService.transitTo(taskId, TaskStatus.MODULE_HIERARCHY_REVIEW, null);
                    execLog.log(taskId, "<<< 暂停 — 等待人工复核模块层级 (已耗时 " + (System.currentTimeMillis() - t0) + "ms)");
                }
                return;
            } catch (Exception e) {
                execLog.log(taskId, "!!! 流水线异常: " + e.getClass().getSimpleName() + " — " + e.getMessage());
                for (StackTraceElement ste : e.getStackTrace()) {
                    if (ste.getClassName().contains("codeinsight")) {
                        execLog.log(taskId, "    at " + ste.getClassName() + "." + ste.getMethodName() + "(" + ste.getFileName() + ":" + ste.getLineNumber() + ")");
                    }
                }
                try {
                    stateMachineService.transitTo(taskId, TaskStatus.FAILED, e.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to transit failed status", ex);
                }
            } finally {
                taskCache.remove(taskId);
            }
        });
    }
}

