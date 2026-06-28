package com.company.codeinsight.modules.model;

import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.modules.model.controller.AiModelController;
import com.company.codeinsight.modules.model.dto.AiModelTestResult;
import com.company.codeinsight.modules.model.entity.AiModel;
import com.company.codeinsight.modules.model.entity.AiModelPreset;
import com.company.codeinsight.modules.model.service.AiModelService;
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
public class AiModelSecurityAndPresetManagementTest {

    @Autowired
    private AiModelController aiModelController;

    @Autowired
    private AiModelService aiModelService;

    @Test
    public void testApiKeyIsHiddenAndBlankUpdateKeepsExistingKey() {
        AiModel model = new AiModel();
        model.setName("Secure Model");
        model.setIdentifier("secure-model");
        model.setProvider("SecureProvider");
        model.setApiKey("secret-key-value");
        model.setBaseUrl("https://api.example.com/v1");
        model.setIsDefault("false");
        model.setCapabilities("text");
        model.setDescription("Secure model");
        model.setSortOrder(100);

        ApiResponse<AiModel> createResponse = aiModelController.createModel(model);
        Long id = createResponse.getData().getId();

        Assertions.assertNull(createResponse.getData().getApiKey());
        Assertions.assertTrue(createResponse.getData().getHasApiKey());

        ApiResponse<AiModel> detailResponse = aiModelController.getModelDetail(id);
        Assertions.assertNull(detailResponse.getData().getApiKey());
        Assertions.assertTrue(detailResponse.getData().getHasApiKey());

        AiModel updatePayload = detailResponse.getData();
        updatePayload.setDescription("Updated without touching key");
        updatePayload.setApiKey("");
        aiModelController.updateModel(id, updatePayload);

        AiModel rawModel = aiModelService.getById(id);
        Assertions.assertEquals("secret-key-value", rawModel.getApiKey());
    }

    @Test
    public void testMockModelConnectionReturnsResultWithoutLeakingKey() {
        AiModel model = new AiModel();
        model.setName("Testable Model");
        model.setIdentifier("testable-model");
        model.setProvider("MockProvider");
        model.setApiKey("test-secret");
        model.setBaseUrl("https://api.example.com/v1");
        model.setIsDefault("false");
        model.setCapabilities("text");
        model.setDescription("Testable model");
        model.setSortOrder(110);

        Long id = aiModelController.createModel(model).getData().getId();

        ApiResponse<AiModelTestResult> response = aiModelController.testModelConnection(id);

        Assertions.assertEquals(0, response.getCode());
        Assertions.assertTrue(response.getData().getSuccess());
        Assertions.assertTrue(response.getData().getDurationMs() >= 0);
        Assertions.assertFalse(response.getData().getMessage().contains("test-secret"));
    }

    @Test
    public void testPresetCanBeManaged() {
        AiModelPreset preset = new AiModelPreset();
        preset.setName("Managed Preset");
        preset.setProvider("ManagedProvider");
        preset.setIdentifier("managed-preset");
        preset.setBaseUrl("https://api.example.com/v1");
        preset.setCapabilities("text");
        preset.setDescription("Preset before update");
        preset.setSortOrder(120);
        preset.setStatus(1);

        Long id = aiModelController.createModelPreset(preset).getData().getId();

        preset.setName("Managed Preset Updated");
        preset.setDescription("Preset after update");
        ApiResponse<AiModelPreset> updateResponse = aiModelController.updateModelPreset(id, preset);
        Assertions.assertEquals("Managed Preset Updated", updateResponse.getData().getName());

        aiModelController.changeModelPresetStatus(id, 0);
        ApiResponse<List<AiModelPreset>> allResponse = aiModelController.listAllModelPresets();
        AiModelPreset disabled = allResponse.getData().stream()
            .filter(item -> id.equals(item.getId()))
            .findFirst()
            .orElseThrow();
        Assertions.assertEquals(0, disabled.getStatus());

        ApiResponse<List<AiModelPreset>> enabledResponse = aiModelController.listModelPresets();
        Assertions.assertTrue(enabledResponse.getData().stream().noneMatch(item -> id.equals(item.getId())));

        aiModelController.deleteModelPreset(id);
        ApiResponse<List<AiModelPreset>> afterDeleteResponse = aiModelController.listAllModelPresets();
        Assertions.assertTrue(afterDeleteResponse.getData().stream().noneMatch(item -> id.equals(item.getId())));
    }
}
