package com.company.codeinsight.modules.auth.dto;

import lombok.Data;

/**
 * 登录成功响应数据
 * 返回前端用于本地会话保存的轻量身份信息和临时访问令牌。
 */
@Data
public class LoginResponse {

    private String token;

    private String username;

    private String displayName;

    private String role;

    private Long expiresInSeconds;
}
