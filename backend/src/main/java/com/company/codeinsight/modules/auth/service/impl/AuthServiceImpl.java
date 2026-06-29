package com.company.codeinsight.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.auth.dto.LoginRequest;
import com.company.codeinsight.modules.auth.dto.LoginResponse;
import com.company.codeinsight.modules.auth.entity.UserAccount;
import com.company.codeinsight.modules.auth.mapper.UserAccountMapper;
import com.company.codeinsight.modules.auth.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 登录认证服务实现
 * MVP 阶段保留配置化账号兜底，后续替换为 UM/SSO 与 JWT。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    @Value("${code-insight.auth.admin-username:admin}")
    private String adminUsername;

    @Value("${code-insight.auth.admin-password:admin123}")
    private String adminPassword;

    @Value("${code-insight.auth.admin-display-name:平台管理员}")
    private String adminDisplayName;

    @Autowired
    private UserAccountMapper userAccountMapper;

    private static final long EXPIRES_IN_SECONDS = 8 * 60 * 60L;

    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request.getPassword();
        String otp = request.getToken();

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password) || !StringUtils.hasText(otp)) {
            throw new BusinessException("UM 账号、UM 密码与平安令牌均为必填");
        }
        if (!adminUsername.equals(username) || !adminPassword.equals(password)) {
            // 不在日志中打印口令原文，仅记录长度便于审计
            log.warn("登录失败：UM 账号 {} 凭据不匹配，令牌长度={}", username, otp.length());
            throw new BusinessException("UM 账号或密码错误");
        }
        // 占位：真实场景下此处调用 UM / 平安令牌接口二次校验
        if (!otp.matches("^\\d{6}$")) {
            throw new BusinessException("平安令牌格式不正确");
        }

        // 写回最近登录时间（基础配置-权限管理占位页用得到）
        try {
            userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                    .eq(UserAccount::getUsername, adminUsername)
                    .set(UserAccount::getLastLoginAt, LocalDateTime.now()));
        } catch (Exception e) {
            // ci_user 表未初始化（旧库）时忽略，不阻塞登录
            log.debug("更新 last_login_at 失败（ci_user 表不存在？）: {}", e.getMessage());
        }

        LoginResponse response = new LoginResponse();
        response.setToken("codeinsight-admin-" + UUID.randomUUID());
        response.setUsername(adminUsername);
        response.setDisplayName(adminDisplayName);
        response.setRole("ADMIN");
        response.setExpiresInSeconds(EXPIRES_IN_SECONDS);
        return response;
    }
}
