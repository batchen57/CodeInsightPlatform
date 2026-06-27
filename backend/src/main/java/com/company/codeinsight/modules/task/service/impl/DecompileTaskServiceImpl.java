package com.company.codeinsight.modules.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.codeinsight.common.exception.BusinessException;
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
     * @param systemId     所属业务系统 ID
     * @param repositoryId 所选代码仓库 ID
     * @param promptVersion 提示词版本
     * @param modelName    大模型名称
     * @return 新增的任务记录对象
     */
    @Override
    @Transactional
    public DecompileTask createInitialTask(Long systemId, Long repositoryId, Integer promptVersion, String modelName,
                                           com.company.codeinsight.modules.entrypoint.model.EntryPointConfig entryScanConfig) {
        // 验证系统和仓库的从属合法性
        validateTaskSource(systemId, repositoryId);

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
        task.setEntryScanConfig(com.company.codeinsight.modules.entrypoint.model.EntryPointConfigCodec.encode(entryScanConfig));

        this.save(task);
        return task;
    }

    /**
     * 创建增量代码分析任务
     */
    @Override
    @Transactional
    public DecompileTask createIncrementalTask(Long systemId, Long repositoryId, Integer promptVersion, String modelName,
                                               com.company.codeinsight.modules.entrypoint.model.EntryPointConfig entryScanConfig) {
        validateTaskSource(systemId, repositoryId);
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
        task.setEntryScanConfig(com.company.codeinsight.modules.entrypoint.model.EntryPointConfigCodec.encode(entryScanConfig));

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

                // 2.5 AST 调用链落表维护（复用 JavaParserServiceImpl.parseFile，把解析产出的 methodCalls 持久化到 ci_method_call）
                methodCallService.persistAstForTask(taskId, projectDir);

                // 3. 进入分片切割提取阶段 (SPLITTING_TASK)
                stateMachineService.transitTo(taskId, TaskStatus.SPLITTING_TASK, null);
                codeChunkService.chunkAndEstimate(taskId, snapshots);

                // 4. 进入大模型 AI 归纳分析阶段 (AI_ANALYZING)
                stateMachineService.transitTo(taskId, TaskStatus.AI_ANALYZING, null);
                List<CodeChunk> chunks = codeChunkService.getChunksByTaskId(taskId);

                // 4.5 进入模块层级提炼阶段 (MODULE_HIERARCHY)：基于 ci_method_call 反查入口，逐一提交 AI 提炼，维护 ModuleHierarchy DTO
                stateMachineService.transitTo(taskId, TaskStatus.MODULE_HIERARCHY, null);
                moduleHierarchyService.buildAndPersist(taskId, projectDir);

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

