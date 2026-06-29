package com.company.codeinsight.modules.system.service;

import com.company.codeinsight.modules.system.entity.SystemApplication;
import com.company.codeinsight.modules.system.enums.SystemState;

/**
 * 系统状态机服务。
 *
 * 转换矩阵：
 *   DRAFT             → REPO_CONFIGURED, DISABLED
 *   REPO_CONFIGURED   → SCAN_CONFIGURED, DISABLED
 *   SCAN_CONFIGURED   → PROMPT_CONFIGURED, DISABLED
 *   PROMPT_CONFIGURED → ACTIVE, DISABLED
 *   ACTIVE            → DISABLED
 *   DISABLED          → ACTIVE
 */
public interface SystemStateMachineService {

    void transitTo(Long systemId, SystemState target);

    void transitTo(SystemApplication system, SystemState target);

    boolean canTransit(SystemState current, SystemState target);
}
