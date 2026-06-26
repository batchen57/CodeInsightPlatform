package com.company.codeinsight.modules.model;

import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.modules.model.controller.AiModelController;
import com.company.codeinsight.modules.model.entity.AiModel;
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
public class AiModelControllerTest {

    @Autowired
    private AiModelController aiModelController;

    @Test
    public void testControllerCrudFlow() {
        // 1. Create a model
        AiModel model = new AiModel();
        model.setName("GPT-4o Mini");
        model.setIdentifier("gpt-4o-mini");
        model.setProvider("OpenAI");
        model.setApiKey("mini-key");
        model.setBaseUrl("https://api.openai.com");
        model.setIsDefault("false");
        model.setCapabilities("text");
        model.setDescription("OpenAI GPT-4o Mini model");
        model.setSortOrder(50);

        ApiResponse<AiModel> createResponse = aiModelController.createModel(model);
        Assertions.assertEquals(0, createResponse.getCode());
        Assertions.assertNotNull(createResponse.getData().getId());

        Long createdId = createResponse.getData().getId();

        // 2. Fetch Detail
        ApiResponse<AiModel> detailResponse = aiModelController.getModelDetail(createdId);
        Assertions.assertEquals(0, detailResponse.getCode());
        Assertions.assertEquals("GPT-4o Mini", detailResponse.getData().getName());

        // 3. Update Model
        AiModel updatePayload = detailResponse.getData();
        updatePayload.setName("GPT-4o Mini (Updated)");
        ApiResponse<AiModel> updateResponse = aiModelController.updateModel(createdId, updatePayload);
        Assertions.assertEquals(0, updateResponse.getCode());

        ApiResponse<AiModel> detailUpdatedResponse = aiModelController.getModelDetail(createdId);
        Assertions.assertEquals("GPT-4o Mini (Updated)", detailUpdatedResponse.getData().getName());

        // 4. List Models
        ApiResponse<List<AiModel>> listResponse = aiModelController.listAllModels();
        Assertions.assertEquals(0, listResponse.getCode());
        Assertions.assertTrue(listResponse.getData().size() > 0);

        // 5. Delete Model
        ApiResponse<Void> deleteResponse = aiModelController.deleteModel(createdId);
        Assertions.assertEquals(0, deleteResponse.getCode());

        ApiResponse<AiModel> detailDeletedResponse = aiModelController.getModelDetail(createdId);
        Assertions.assertNull(detailDeletedResponse.getData());
    }
}
