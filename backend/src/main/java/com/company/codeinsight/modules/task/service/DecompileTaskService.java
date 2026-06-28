package com.company.codeinsight.modules.task.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
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
     * @param statuses 多状态过滤（如 chip 分组过滤时使用），与 status 单值互斥
     */
    Page<DecompileTask> listTasksPage(int current, int size, Long systemId, String status, String type, List<String> statuses);

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

