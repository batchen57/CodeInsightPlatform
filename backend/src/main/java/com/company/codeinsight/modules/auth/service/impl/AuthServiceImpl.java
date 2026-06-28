package com.company.codeinsight.modules.auth.service.impl;

import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.auth.dto.LoginRequest;
import com.company.codeinsight.modules.auth.dto.LoginResponse;
import com.company.codeinsight.modules.auth.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

        LoginResponse response = new LoginResponse();
        response.setToken("codeinsight-admin-" + UUID.randomUUID());
        response.setUsername(adminUsername);
        response.setDisplayName(adminDisplayName);
        response.setRole("ADMIN");
        response.setExpiresInSeconds(EXPIRES_IN_SECONDS);
        return response;
    }
}
