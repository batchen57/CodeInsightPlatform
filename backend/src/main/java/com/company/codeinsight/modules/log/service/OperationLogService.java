package com.company.codeinsight.modules.log.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.log.entity.OperationLog;

/**
 * 操作日志服务接口
 * 负责日志快捷写入和多条件分页查询的定义。
 */
public interface OperationLogService extends IService<OperationLog> {

    /**
     * 便捷异步/同步写入系统及任务操作日志记录
     *
     * @param systemId     系统 ID
     * @param taskId       任务 ID
     * @param actionType   动作类型
     * @param detail       操作详细载荷/参数描述
     * @param exceptionMsg 若发生异常，详细的异常堆栈说明
     * @param success      本次操作是否执行成功
     */
    void logOperation(Long systemId, Long taskId, String actionType, String detail, String exceptionMsg, boolean success);

    /**
     * 分页、条件查询操作审计日志列表
     */
    Page<OperationLog> listLogsPage(int current, int size, Long systemId, Long taskId, String username, String actionType, Integer isSuccess);
}

