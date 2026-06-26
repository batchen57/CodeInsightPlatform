package com.company.codeinsight.modules.log.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.log.entity.OperationLog;

public interface OperationLogService extends IService<OperationLog> {
    void logOperation(Long systemId, Long taskId, String actionType, String detail, String exceptionMsg, boolean success);
    Page<OperationLog> listLogsPage(int current, int size, Long systemId, Long taskId, String username, String actionType, Integer isSuccess);
}
