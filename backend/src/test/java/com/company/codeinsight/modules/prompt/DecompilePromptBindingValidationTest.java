package com.company.codeinsight.modules.prompt;

import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.prompt.service.DecompilePromptService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统/任务提示词绑定校验：未绑定时必须抛 BusinessException，禁止运行时兜底。
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@ActiveProfiles("test")
@Transactional
public class DecompilePromptBindingValidationTest {

    @Autowired
    private DecompilePromptService decompilePromptService;

    @Test
    public void validateSystemPromptBinding_rejectsMissingSystem() {
        BusinessException ex = Assertions.assertThrows(BusinessException.class,
                () -> decompilePromptService.validateSystemPromptBinding(9_999_999L));
        Assertions.assertTrue(ex.getMessage().contains("系统"));
    }

    @Test
    public void isSystemPromptsConfigured_returnsFalseForMissingSystem() {
        Assertions.assertFalse(decompilePromptService.isSystemPromptsConfigured(9_999_999L));
        Assertions.assertNotNull(decompilePromptService.getSystemPromptsConfigurationMessage(9_999_999L));
    }

    @Test
    public void validateTaskPromptBinding_rejectsNullIds() {
        BusinessException ex = Assertions.assertThrows(BusinessException.class,
                () -> decompilePromptService.validateTaskPromptBinding(null, null));
        Assertions.assertTrue(ex.getMessage().contains("模块提取"));
    }
}
