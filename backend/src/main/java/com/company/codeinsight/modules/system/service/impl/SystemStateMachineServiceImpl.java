package com.company.codeinsight.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.log.service.OperationLogService;
import com.company.codeinsight.modules.system.entity.SystemApplication;
import com.company.codeinsight.modules.system.enums.SystemState;
import com.company.codeinsight.modules.system.mapper.SystemApplicationMapper;
import com.company.codeinsight.modules.system.service.SystemStateMachineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 系统状态机实现：转换合法性校验 + 持久化 + 同步旧 status 字段 + 写操作审计。
 */
@Service
public class SystemStateMachineServiceImpl
        extends ServiceImpl<SystemApplicationMapper, SystemApplication>
        implements SystemStateMachineService {

    @Autowired
    private OperationLogService operationLogService;

    /** 当前态 → 允许的下一态 */
    private static final Map<SystemState, Set<SystemState>> TRANSITIONS = Map.of(
            SystemState.DRAFT,             EnumSet.of(SystemState.REPO_CONFIGURED, SystemState.DISABLED),
            SystemState.REPO_CONFIGURED,   EnumSet.of(SystemState.SCAN_CONFIGURED, SystemState.DISABLED),
            SystemState.SCAN_CONFIGURED,   EnumSet.of(SystemState.PROMPT_CONFIGURED, SystemState.DISABLED),
            SystemState.PROMPT_CONFIGURED, EnumSet.of(SystemState.ACTIVE, SystemState.DISABLED),
            SystemState.ACTIVE,            EnumSet.of(SystemState.DISABLED),
            SystemState.DISABLED,          EnumSet.of(SystemState.ACTIVE)
    );

    @Override
    public boolean canTransit(SystemState current, SystemState target) {
        if (current == null || target == null) return false;
        if (current == target) return true; // 幂等：同一态重复进入视为合法
        return TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
    }

    @Override
    public void transitTo(Long systemId, SystemState target) {
        if (systemId == null || target == null) {
            throw new BusinessException("systemId/target 不能为空");
        }
        SystemApplication system = this.getById(systemId);
        if (system == null) {
            throw new BusinessException("系统不存在：id=" + systemId);
        }
        transitTo(system, target);
    }

    @Override
    public void transitTo(SystemApplication system, SystemState target) {
        if (system == null || target == null) {
            throw new BusinessException("系统/target 不能为空");
        }
        SystemState current = SystemState.parse(system.getState());
        if (!canTransit(current, target)) {
            throw new BusinessException("状态 " + current + " 不能直接进入 " + target);
        }
        if (current == target) {
            // 幂等：不重复写
            return;
        }

        // 1) 更新 state + 同步 status（ACTIVE=1, DISABLED=0，其他=0）
        Integer legacyStatus = (target == SystemState.ACTIVE) ? 1 : 0;
        LocalDateTime now = LocalDateTime.now();
        this.update(new LambdaUpdateWrapper<SystemApplication>()
                .eq(SystemApplication::getId, system.getId())
                .set(SystemApplication::getState, target.name())
                .set(SystemApplication::getStatus, legacyStatus)
                .set(SystemApplication::getUpdatedAt, now));

        // 2) 写操作审计
        operationLogService.logOperation(
                system.getId(),
                null,
                "SYSTEM_TRANSIT",
                "系统状态变更: " + current + " -> " + target,
                null,
                true
        );

        system.setState(target.name());
        system.setStatus(legacyStatus);
    }
}
