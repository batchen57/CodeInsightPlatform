package com.company.codeinsight.modules.auth.service;

import com.company.codeinsight.modules.auth.dto.LoginRequest;
import com.company.codeinsight.modules.auth.dto.LoginResponse;

/**
 * 登录认证服务
 * 负责校验本地管理员账号并生成前端会话令牌。
 */
public interface AuthService {

    LoginResponse login(LoginRequest request);
}
