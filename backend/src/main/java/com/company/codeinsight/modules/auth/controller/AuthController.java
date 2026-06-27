package com.company.codeinsight.modules.auth.controller;

import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.modules.auth.dto.LoginRequest;
import com.company.codeinsight.modules.auth.dto.LoginResponse;
import com.company.codeinsight.modules.auth.service.AuthService;
import com.company.codeinsight.modules.log.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录认证控制器
 * 提供前端登录页所需的管理员登录接口。
 */
@Tag(name = "登录认证", description = "平台登录与会话初始化")
@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private OperationLogService operationLogService;

    /**
     * 管理员登录
     */
    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        operationLogService.logOperation(null, null, "LOGIN", "用户登录: " + response.getUsername(), null, true);
        return ApiResponse.success(response);
    }
}
