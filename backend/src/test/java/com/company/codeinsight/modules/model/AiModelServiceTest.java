package com.company.codeinsight.modules.model;

import com.company.codeinsight.modules.model.entity.AiModel;
import com.company.codeinsight.modules.model.service.AiModelService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AiModelServiceTest {

    @Autowired
    private AiModelService aiModelService;

    @Test
    public void testModelLifecycleAndDefaults() {
        // 1. 创建第一个模型并设为默认
        AiModel m1 = new AiModel();
        m1.setName("Gemini Pro");
        m1.setIdentifier("gemini-pro");
        m1.setProvider("Google");
        m1.setApiKey("test-key-1");
        m1.setBaseUrl("https://api.google.com");
        m1.setIsDefault("true");
        m1.setCapabilities("text,image");
        m1.setDescription("Google Model");
        m1.setSortOrder(10);
        aiModelService.saveModel(m1);

        Assertions.assertNotNull(m1.getId());
        Assertions.assertEquals("true", m1.getIsDefault());

        // 2. 创建第二个模型并也设为默认，应该清除第一个的默认状态
        AiModel m2 = new AiModel();
        m2.setName("DeepSeek Chat");
        m2.setIdentifier("deepseek-chat");
        m2.setProvider("DeepSeek");
        m2.setApiKey("test-key-2");
        m2.setBaseUrl("https://api.deepseek.com");
        m2.setIsDefault("true");
        m2.setCapabilities("text");
        m2.setDescription("DeepSeek Model");
        m2.setSortOrder(20);
        aiModelService.saveModel(m2);

        // 重新获取第一个模型
        AiModel m1Reloaded = aiModelService.getById(m1.getId());
        Assertions.assertEquals("false", m1Reloaded.getIsDefault());
        Assertions.assertEquals("true", m2.getIsDefault());

        // 3. 测试排序查询
        List<AiModel> list = aiModelService.listAllModelsSorted();
        Assertions.assertTrue(list.size() >= 2);
        
        // 确保 m1 排在 m2 前面，因为 m1.sortOrder(10) < m2.sortOrder(20)
        int indexM1 = -1;
        int indexM2 = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(m1.getId())) {
                indexM1 = i;
            } else if (list.get(i).getId().equals(m2.getId())) {
                indexM2 = i;
            }
        }
        Assertions.assertTrue(indexM1 < indexM2);
    }
}
