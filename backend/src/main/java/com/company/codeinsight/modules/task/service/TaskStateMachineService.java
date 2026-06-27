package com.company.codeinsight.modules.task.service;

import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.enums.TaskStatus;

/**
 * 任务状态机控制管理服务接口
 * 负责定义任务状态跃迁时的合法性校验、阶段进度数值更新以及历史流转记录的业务接口。
 */
public interface TaskStateMachineService {

    /**
     * 将任务状态强行跃迁至目标状态（自动持久化状态并记录错误理由）
     *
     * @param taskId       任务 ID
     * @param targetStatus 跃迁的目标状态枚举
     * @param errorReason  若流转至失败状态，详细的异常失败描述说明
     */
    void transitTo(Long taskId, TaskStatus targetStatus, String errorReason);

    /**
     * 将任务实体状态状态跃迁至目标状态
     *
     * @param task         任务实体对象
     * @param targetStatus 跃迁的目标状态枚举
     * @param errorReason  若流转至失败状态，详细的异常失败描述说明
     */
    void transitTo(DecompileTask task, TaskStatus targetStatus, String errorReason);

    /**
     * 判断当前状态是否被允许流转跃迁到目标状态，以约束状态机转移规则
     *
     * @param current 当前状态
     * @param target  目标状态
     * @return 是否符合状态跃迁的流转路径规则
     */
    boolean canTransit(TaskStatus current, TaskStatus target);
}

