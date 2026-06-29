package com.company.codeinsight.modules.system.enums;

import java.util.Set;

/**
 * 系统状态机：6 态细粒度
 *  DRAFT               — 基本信息已填，等待配仓库
 *  REPO_CONFIGURED     — 至少 1 个仓库已添加，等待配入口扫描
 *  SCAN_CONFIGURED     — 至少 1 个仓库的入口扫描已配置，等待配提示词
 *  PROMPT_CONFIGURED   — 提示词已选择，等待启用
 *  ACTIVE              — 已启用，可创建任务
 *  DISABLED            — 已停用
 */
public enum SystemState {
    DRAFT,
    REPO_CONFIGURED,
    SCAN_CONFIGURED,
    PROMPT_CONFIGURED,
    ACTIVE,
    DISABLED;

    /**
     * 解析字符串，非法值默认 DRAFT。
     */
    public static SystemState parse(String s) {
        if (s == null || s.isBlank()) return DRAFT;
        try {
            return SystemState.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DRAFT;
        }
    }

    /**
     * 用于 listSystems 的 status 参数兼容（status=1 表示历史启用状态，对应 ACTIVE）。
     */
    public static Set<SystemState> fromLegacyStatus(Integer legacyStatus) {
        if (legacyStatus == null) return null;
        if (legacyStatus == 1) return Set.of(ACTIVE);
        // status=0：未启用，包含所有未启用态
        return Set.of(DRAFT, REPO_CONFIGURED, SCAN_CONFIGURED, PROMPT_CONFIGURED, DISABLED);
    }

    /** 是否已启用（可被任务流程使用） */
    public boolean isEnabled() {
        return this == ACTIVE;
    }
}
