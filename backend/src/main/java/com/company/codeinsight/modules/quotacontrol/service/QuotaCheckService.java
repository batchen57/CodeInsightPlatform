package com.company.codeinsight.modules.quotacontrol.service;

import com.company.codeinsight.common.auth.OperatorContext;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.quotacontrol.entity.UserQuota;
import com.company.codeinsight.modules.token.service.TokenAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 额度前置检查：在 AI 调用前调用，验证用户日/月 Token 是否充足。
 * <p>
 * 配置读取统一从 {@link SystemConfigService} 走（ci_system_config）；
 * 用户额度从 {@link UserQuotaService} 走（ci_user_quota）。0 表示不限。
 * </p>
 */
@Slf4j
@Service
public class QuotaCheckService {

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private UserQuotaService userQuotaService;

    @Autowired
    private TokenAuditService tokenAuditService;

    /**
     * 检查当前用户额度（userId 取自 OperatorContext）。不通过抛 BusinessException。
     *
     * @param estimatedTokens 本次 AI 调用的预估 Token 数（≤0 跳过）
     */
    public void checkUserQuota(int estimatedTokens) {
        // 全局开关：limit-enabled=false 时跳过所有限额检查
        if (!systemConfigService.getBoolean("token.limit-enabled", true)) {
            return;
        }

        Long userId = OperatorContext.getUserId();
        if (userId == null) {
            return; // 无 user 上下文时跳过
        }

        UserQuota quota = userQuotaService.findByUserId(userId);
        if (quota == null || quota.getEnabled() == null || quota.getEnabled() == 0) {
            return; // 无配置或未启用，跳过
        }

        if (estimatedTokens <= 0) {
            return;
        }

        // 日额度检查
        if (quota.getDailyTokenLimit() != null && quota.getDailyTokenLimit() > 0) {
            int used = tokenAuditService.getUserDailyTokens(userId);
            if (used + estimatedTokens > quota.getDailyTokenLimit()) {
                throw new BusinessException("用户日额度超限：限制 " + quota.getDailyTokenLimit()
                        + "，已用 " + used + "，本次预估 " + estimatedTokens);
            }
        }
        // 月额度检查
        if (quota.getMonthlyTokenLimit() != null && quota.getMonthlyTokenLimit() > 0) {
            int used = tokenAuditService.getUserMonthlyTokens(userId);
            if (used + estimatedTokens > quota.getMonthlyTokenLimit()) {
                throw new BusinessException("用户月额度超限：限制 " + quota.getMonthlyTokenLimit()
                        + "，已用 " + used + "，本次预估 " + estimatedTokens);
            }
        }
        log.debug("user quota precheck passed: userId={}, dailyLimit={}, monthlyLimit={}, estimate={}",
                userId, quota.getDailyTokenLimit(), quota.getMonthlyTokenLimit(), estimatedTokens);
    }

    /**
     * 配置变更后失效（保留扩展位，目前无需做）。
     */
    public void refresh() {
        // No state held.
    }
}
