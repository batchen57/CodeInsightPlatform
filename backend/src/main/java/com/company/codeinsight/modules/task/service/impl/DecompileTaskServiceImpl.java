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

@Slf4j
@Service
public class DecompileTaskServiceImpl extends ServiceImpl<DecompileTaskMapper, DecompileTask> implements DecompileTaskService {

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

    @Override
    @Transactional
    public DecompileTask createInitialTask(Long systemId, Long repositoryId, Integer promptVersion, String modelName) {
        validateTaskSource(systemId, repositoryId);
        DecompileTask task = new DecompileTask();
        task.setSystemId(systemId);
        task.setRepositoryId(repositoryId);
        task.setPromptVersion(promptVersion);
        task.setStatus(TaskStatus.DRAFT.name());
        task.setType("INITIAL");
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

    @Override
    @Transactional
    public DecompileTask createIncrementalTask(Long systemId, Long repositoryId, Integer promptVersion, String modelName) {
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
        
        this.save(task);
        return task;
    }

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

    @Override
    public void startTask(Long id) {
        DecompileTask task = this.getById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        taskCache.put(task.getId(), task);
        stateMachineService.transitTo(task, TaskStatus.PENDING, null);

        // 运行任务管道
        runPipeline(task.getId());
    }

    @Override
    @Transactional
    public void terminateTask(Long id) {
        DecompileTask task = this.getById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        stateMachineService.transitTo(task, TaskStatus.CANCELLED, "用户终止了任务");
    }

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

    private void runPipeline(Long taskId) {
        CompletableFuture.runAsync(() -> {
            try {
                // 1. PULLING_CODE
                stateMachineService.transitTo(taskId, TaskStatus.PULLING_CODE, null);
                DecompileTask task = this.getById(taskId);
                if (task == null) {
                    task = taskCache.get(taskId);
                }
                if (task == null) {
                    throw new BusinessException("任务不存在, ID: " + taskId);
                }
                
                File projectDir = codeScannerService.pullAndScan(taskId, task.getRepositoryId());

                // 2. PARSING_CODE
                stateMachineService.transitTo(taskId, TaskStatus.PARSING_CODE, null);
                List<com.company.codeinsight.modules.scanner.entity.CodeFileSnapshot> snapshots = codeScannerService.getSnapshotsByTaskId(taskId);

                // 3. SPLITTING_TASK
                stateMachineService.transitTo(taskId, TaskStatus.SPLITTING_TASK, null);
                codeChunkService.chunkAndEstimate(taskId, snapshots);

                // 4. AI_ANALYZING
                stateMachineService.transitTo(taskId, TaskStatus.AI_ANALYZING, null);
                List<CodeChunk> chunks = codeChunkService.getChunksByTaskId(taskId);

                // 获取使用的提示词
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

                // 5. GENERATING_DOC
                stateMachineService.transitTo(taskId, TaskStatus.GENERATING_DOC, null);
                aiSummaryService.generateDraftDocument(taskId, chunks, promptContent);

                // 6. PENDING_REVIEW
                stateMachineService.transitTo(taskId, TaskStatus.PENDING_REVIEW, null);

            } catch (Exception e) {
                log.error("Pipeline run failed for task " + taskId, e);
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
