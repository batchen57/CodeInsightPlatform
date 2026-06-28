package com.company.codeinsight.modules.model;

import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.modules.model.controller.AiModelController;
import com.company.codeinsight.modules.model.entity.AiModelPreset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@ActiveProfiles("test")
@Transactional
public class AiModelPresetControllerTest {

    @Autowired
    private AiModelController aiModelController;

    @Test
    public void testCreateAndListPresets() {
        AiModelPreset preset = new AiModelPreset();
        preset.setName("Test Preset Model");
        preset.setProvider("TestProvider");
        preset.setIdentifier("test-preset-model");
        preset.setBaseUrl("https://api.example.com/v1");
        preset.setCapabilities("text,image");
        preset.setDescription("测试用预设模型");
        preset.setSortOrder(3);
        preset.setStatus(1);

        ApiResponse<AiModelPreset> createResponse = aiModelController.createModelPreset(preset);

        Assertions.assertEquals(0, createResponse.getCode());
        Assertions.assertNotNull(createResponse.getData().getId());
        Assertions.assertEquals("Test Preset Model", createResponse.getData().getName());

        ApiResponse<List<AiModelPreset>> listResponse = aiModelController.listModelPresets();

        Assertions.assertEquals(0, listResponse.getCode());
        Assertions.assertTrue(
            listResponse.getData().stream().anyMatch(item -> "test-preset-model".equals(item.getIdentifier()))
        );
    }
}
