package com.company.codeinsight.modules.task.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.task.entity.DecompileTask;

public interface DecompileTaskService extends IService<DecompileTask> {
    Page<DecompileTask> listTasksPage(int current, int size, Long systemId, String status, String type);
    DecompileTask createInitialTask(Long systemId, Long repositoryId, Integer promptVersion, String modelName);
    DecompileTask createIncrementalTask(Long systemId, Long repositoryId, Integer promptVersion, String modelName);
    void startTask(Long id);
    void terminateTask(Long id);
    void retryTask(Long id);
}
