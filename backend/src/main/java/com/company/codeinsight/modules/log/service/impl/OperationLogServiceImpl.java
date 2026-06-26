package com.company.codeinsight.modules.log.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.codeinsight.modules.log.entity.OperationLog;
import com.company.codeinsight.modules.log.mapper.OperationLogMapper;
import com.company.codeinsight.modules.log.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    @Override
    public void logOperation(Long systemId, Long taskId, String actionType, String detail, String exceptionMsg, boolean success) {
        OperationLog log = new OperationLog();
        log.setSystemId(systemId);
        log.setTaskId(taskId);
        log.setUserId(1L); // Mock user ID for MVP
        log.setUsername("Owner"); // Mock username
        log.setActionType(actionType);
        log.setDetail(detail);
        log.setIpAddress("127.0.0.1");
        log.setExceptionMsg(exceptionMsg);
        log.setIsSuccess(success ? 1 : 0);
        log.setCreatedAt(LocalDateTime.now());
        this.save(log);
    }

    @Override
    public Page<OperationLog> listLogsPage(int current, int size, Long systemId, Long taskId, String username, String actionType, Integer isSuccess) {
        Page<OperationLog> page = new Page<>(current, size);
        LambdaQueryWrapper<OperationLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(systemId != null, OperationLog::getSystemId, systemId)
                .eq(taskId != null, OperationLog::getTaskId, taskId)
                .like(StringUtils.hasText(username), OperationLog::getUsername, username)
                .eq(StringUtils.hasText(actionType), OperationLog::getActionType, actionType)
                .eq(isSuccess != null, OperationLog::getIsSuccess, isSuccess)
                .orderByDesc(OperationLog::getCreatedAt);
        return this.page(page, queryWrapper);
    }
}
