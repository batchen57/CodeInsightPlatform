package com.company.codeinsight.modules.model;

import com.company.codeinsight.modules.model.dto.AiModelTestResult;
import com.company.codeinsight.modules.model.entity.AiModel;
import com.company.codeinsight.modules.model.service.impl.AiModelServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;

class AiModelServiceImplTest {

    @Test
    void testModelConnectionValidatesRealModelConfigEvenWhenGlobalMockEnabled() {
        AiModel model = new AiModel();
        model.setId(1L);
        model.setName("真实模型");
        model.setIdentifier("real-model");
        model.setApiKey("secret-key");

        TestableAiModelService service = new TestableAiModelService(model);
        ReflectionTestUtils.setField(service, "mockAiEnabled", true);

        AiModelTestResult result = service.testModelConnection(model.getId());

        Assertions.assertFalse(result.getSuccess());
        Assertions.assertEquals("模型接口地址未配置", result.getMessage());
    }

    private static class TestableAiModelService extends AiModelServiceImpl {
        private final AiModel model;

        private TestableAiModelService(AiModel model) {
            this.model = model;
        }

        @Override
        public AiModel getById(Serializable id) {
            return model;
        }
    }
}
