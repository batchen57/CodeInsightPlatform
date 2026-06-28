package com.company.codeinsight.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 登录请求参数
 * 承载前端登录页提交的 UM 账号、UM 密码与平安动态令牌。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "请输入 UM 账号")
    private String username;

    @NotBlank(message = "请输入 UM 密码")
    private String password;

    /**
     * 平安动态令牌（6 位数字口令）
     */
    @NotBlank(message = "请输入平安令牌")
    @Pattern(regexp = "^\\d{6}$", message = "平安令牌须为 6 位数字")
    private String token;
}
