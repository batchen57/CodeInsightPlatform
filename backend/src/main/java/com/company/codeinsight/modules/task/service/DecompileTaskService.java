package com.company.codeinsight.modules.task.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
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
     * @param systemId      关联系统 ID
     * @param repositoryId  关联代码库 ID
     * @param promptVersion 提示词模板版本
     * @param modelName     选定大模型标识
     * @return 刚创建的 DecompileTask 对象
     */
    DecompileTask createInitialTask(Long systemId, Long repositoryId, Integer promptVersion, String modelName);

    /**
     * 创建一个全新的增量更新分析任务，根据 Git 变更选择性扫描分析
     *
     * @param systemId      关联系统 ID
     * @param repositoryId  关联代码库 ID
     * @param promptVersion 提示词模板版本
     * @param modelName     选定大模型标识
     * @return 刚创建的 DecompileTask 对象
     */
    DecompileTask createIncrementalTask(Long systemId, Long repositoryId, Integer promptVersion, String modelName);

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
}

