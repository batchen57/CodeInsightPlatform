package com.company.codeinsight.modules.task.service;

import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.enums.TaskStatus;

public interface TaskStateMachineService {
    void transitTo(Long taskId, TaskStatus targetStatus, String errorReason);
    void transitTo(DecompileTask task, TaskStatus targetStatus, String errorReason);
    boolean canTransit(TaskStatus current, TaskStatus target);
}
