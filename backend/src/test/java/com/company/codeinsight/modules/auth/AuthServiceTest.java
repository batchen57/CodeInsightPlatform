package com.company.codeinsight.modules.auth;

import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.auth.dto.LoginRequest;
import com.company.codeinsight.modules.auth.dto.LoginResponse;
import com.company.codeinsight.modules.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    @Test
    void loginReturnsTokenForConfiguredAdmin() {
        AuthServiceImpl service = createService();
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");
        request.setToken("123456");

        LoginResponse response = service.login(request);

        Assertions.assertEquals("admin", response.getUsername());
        Assertions.assertEquals("平台管理员", response.getDisplayName());
        Assertions.assertTrue(response.getToken().startsWith("codeinsight-admin-"));
    }

    @Test
    void loginRejectsInvalidPassword() {
        AuthServiceImpl service = createService();
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong");
        request.setToken("123456");

        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> service.login(request));

        Assertions.assertEquals("UM 账号或密码错误", exception.getMessage());
    }

    private AuthServiceImpl createService() {
        AuthServiceImpl service = new AuthServiceImpl();
        ReflectionTestUtils.setField(service, "adminUsername", "admin");
        ReflectionTestUtils.setField(service, "adminPassword", "admin123");
        ReflectionTestUtils.setField(service, "adminDisplayName", "平台管理员");
        return service;
    }
}
