package com.company.codeinsight.modules.prompt;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.modules.prompt.dto.PromptTestResultDto;
import com.company.codeinsight.modules.prompt.entity.DecompilePrompt;
import com.company.codeinsight.modules.prompt.service.DecompilePromptService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@ActiveProfiles("test")
@Transactional
public class DecompilePromptServiceTests {

    @Autowired
    private DecompilePromptService decompilePromptService;

    @Test
    public void testCrud() {
        DecompilePrompt prompt = new DecompilePrompt();
        prompt.setName("Decompile Class summary template");
        prompt.setContent("Please analyze class ${class_name} and methods ${method_name}. Code:\n${source_code}");
        prompt.setVersion(1);
        prompt.setStatus(1);
        prompt.setIsDefault(1);

        // Save
        boolean saved = decompilePromptService.save(prompt);
        Assertions.assertTrue(saved);
        Assertions.assertNotNull(prompt.getId());

        // Get
        DecompilePrompt fetched = decompilePromptService.getById(prompt.getId());
        Assertions.assertEquals("Decompile Class summary template", fetched.getName());

        // List
        Page<DecompilePrompt> page = decompilePromptService.listPromptsPage(1, 10, "Decompile", null, null);
        Assertions.assertTrue(page.getTotal() > 0);

        // Clone
        DecompilePrompt cloned = decompilePromptService.clonePrompt(prompt.getId());
        Assertions.assertNotNull(cloned.getId());
        Assertions.assertEquals("Decompile Class summary template - 副本", cloned.getName());

        // Variable replacement
        Map<String, String> vars = new HashMap<>();
        vars.put("class_name", "MyController");
        vars.put("method_name", "getData");
        vars.put("source_code", "public class MyController {}");
        String result = decompilePromptService.replaceVariables(prompt.getContent(), vars);
        Assertions.assertTrue(result.contains("MyController"));
        Assertions.assertTrue(result.contains("getData"));

        // Status change
        decompilePromptService.changeStatus(prompt.getId(), 0);
        DecompilePrompt statusChanged = decompilePromptService.getById(prompt.getId());
        Assertions.assertEquals(0, statusChanged.getStatus());

        // Delete
        boolean removed = decompilePromptService.removeById(prompt.getId());
        Assertions.assertTrue(removed);
    }

    @Test
    public void testTrialRun() {
        DecompilePrompt prompt = new DecompilePrompt();
        prompt.setName("Trial Run Test");
        prompt.setContent("Class: ${class_name}, Method: ${method_name}, Code: ${source_code}");
        prompt.setVersion(1);
        prompt.setStatus(1);
        decompilePromptService.save(prompt);

        String sampleCode = "public class OrderService {\n" +
                "  private OrderMapper orderMapper;\n" +
                "  public void createOrder(Order order) {\n" +
                "    orderMapper.insert(order);\n" +
                "  }\n" +
                "}";

        PromptTestResultDto result = decompilePromptService.testRun(prompt.getId(), sampleCode, null);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.getInputTokens() > 0);
        Assertions.assertTrue(result.getDurationMs() >= 0);
        
        String output = result.getResult();
        Assertions.assertNotNull(output);
        Assertions.assertTrue(output.contains("OrderService"));
        Assertions.assertTrue(output.contains("createOrder"));
        Assertions.assertTrue(output.contains("orderMapper"));
    }
}
