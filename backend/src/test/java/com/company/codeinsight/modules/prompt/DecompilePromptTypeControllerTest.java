package com.company.codeinsight.modules.prompt;

import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.prompt.controller.DecompilePromptController;
import com.company.codeinsight.modules.prompt.entity.DecompilePrompt;
import com.company.codeinsight.modules.prompt.service.DecompilePromptService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@ActiveProfiles("test")
@Transactional
public class DecompilePromptTypeControllerTest {

    @Autowired
    private DecompilePromptController decompilePromptController;

    @Autowired
    private DecompilePromptService decompilePromptService;

    @Test
    public void testPromptCrudIsIsolatedByPromptType() {
        DecompilePrompt modularPrompt = buildPrompt("模块提取默认", "MODULARIZE", 1);
        Long modularId = decompilePromptController.createPrompt(modularPrompt).getData().getId();

        DecompilePrompt documentPrompt = buildPrompt("文档生成默认", "DOCUMENT_GENERATION", 1);
        Long documentId = decompilePromptController.createPrompt(documentPrompt).getData().getId();

        ApiResponse<PageResult<DecompilePrompt>> modularPage =
            decompilePromptController.listPrompts(1, 20, null, null, "MODULARIZE");
        Assertions.assertTrue(modularPage.getData().getRecords().stream()
            .anyMatch(item -> modularId.equals(item.getId())));
        Assertions.assertTrue(modularPage.getData().getRecords().stream()
            .noneMatch(item -> "DOCUMENT_GENERATION".equals(item.getPromptType())));

        ApiResponse<PageResult<DecompilePrompt>> documentPage =
            decompilePromptController.listPrompts(1, 20, null, null, "DOCUMENT_GENERATION");
        Assertions.assertTrue(documentPage.getData().getRecords().stream()
            .anyMatch(item -> documentId.equals(item.getId())));
        Assertions.assertTrue(documentPage.getData().getRecords().stream()
            .noneMatch(item -> "MODULARIZE".equals(item.getPromptType())));

        DecompilePrompt anotherModularDefault = buildPrompt("模块提取默认二", "MODULARIZE", 1);
        Long anotherModularId = decompilePromptController.createPrompt(anotherModularDefault).getData().getId();

        Assertions.assertEquals(0, decompilePromptService.getById(modularId).getIsDefault());
        Assertions.assertEquals(1, decompilePromptService.getById(anotherModularId).getIsDefault());
        Assertions.assertEquals(1, decompilePromptService.getById(documentId).getIsDefault());

        DecompilePrompt clonedDocument = decompilePromptController.clonePrompt(documentId).getData();
        Assertions.assertEquals("DOCUMENT_GENERATION", clonedDocument.getPromptType());
        Assertions.assertEquals(0, clonedDocument.getIsDefault());
        Assertions.assertEquals(0, clonedDocument.getStatus());
    }

    private DecompilePrompt buildPrompt(String name, String promptType, Integer isDefault) {
        DecompilePrompt prompt = new DecompilePrompt();
        prompt.setName(name);
        prompt.setContent("Class: ${class_name}, Method: ${method_name}, Code: ${source_code}");
        prompt.setVersion(1);
        prompt.setStatus(1);
        prompt.setIsDefault(isDefault);
        prompt.setPromptType(promptType);
        return prompt;
    }
}
