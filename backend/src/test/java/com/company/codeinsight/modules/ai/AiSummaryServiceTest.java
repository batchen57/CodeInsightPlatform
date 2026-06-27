package com.company.codeinsight.modules.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.codeinsight.modules.ai.entity.AiCallRecord;
import com.company.codeinsight.modules.ai.service.impl.AiSummaryServiceImpl;
import com.company.codeinsight.modules.ai.mapper.AiCallRecordMapper;
import com.company.codeinsight.modules.ai.service.AiSummaryService;
import com.company.codeinsight.modules.chunk.entity.CodeChunk;
import com.company.codeinsight.modules.chunk.mapper.CodeChunkMapper;
import com.company.codeinsight.modules.draft.entity.KnowledgeDraft;
import com.company.codeinsight.modules.draft.mapper.KnowledgeDraftMapper;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.mapper.DecompileTaskMapper;
import com.company.codeinsight.modules.token.entity.TokenUsageAudit;
import com.company.codeinsight.modules.token.mapper.TokenUsageAuditMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AiSummaryServiceTest {

    @Autowired
    private AiSummaryService aiSummaryService;

    @Autowired
    private CodeChunkMapper chunkMapper;

    @Autowired
    private DecompileTaskMapper taskMapper;

    @Autowired
    private AiCallRecordMapper aiCallRecordMapper;

    @Autowired
    private TokenUsageAuditMapper tokenUsageAuditMapper;

    @Autowired
    private KnowledgeDraftMapper knowledgeDraftMapper;

    @Test
    public void testSummaryAndDraftGeneration() {
        Long taskId = 777L;

        // 1. 创建 Task
        DecompileTask task = new DecompileTask();
        task.setId(taskId);
        task.setSystemId(1L);
        task.setRepositoryId(1L);
        task.setStatus("PENDING");
        task.setType("INITIAL");
        task.setProgress(0);
        taskMapper.insert(task);

        // 2. 创建 Chunk
        CodeChunk chunk = new CodeChunk();
        chunk.setTaskId(taskId);
        chunk.setFilePath("src/main/java/com/demo/controller/UserController.java");
        chunk.setClassName("UserController");
        chunk.setMethodName("listUsers");
        chunk.setChunkType("METHOD");
        chunk.setContentHash("hash12345");
        chunk.setStartLine(10);
        chunk.setEndLine(15);
        chunk.setTokenEstimate(20);
        chunk.setStatus("PENDING");
        chunk.setCreatedAt(LocalDateTime.now());
        chunkMapper.insert(chunk);

        // 3. 产生总结
        String summary = aiSummaryService.summarizeChunk(taskId, chunk.getId(), "测试提示词", "MiniMax-M3");
        Assertions.assertNotNull(summary);

        // 验证 AI 调用记录
        List<AiCallRecord> records = aiCallRecordMapper.selectList(
                new LambdaQueryWrapper<AiCallRecord>().eq(AiCallRecord::getTaskId, taskId)
        );
        Assertions.assertFalse(records.isEmpty());

        // 验证 Token 审计
        List<TokenUsageAudit> audits = tokenUsageAuditMapper.selectList(
                new LambdaQueryWrapper<TokenUsageAudit>().eq(TokenUsageAudit::getTaskId, taskId)
        );
        Assertions.assertFalse(audits.isEmpty());

        // 4. 合并产生模块 Markdown 草稿
        List<CodeChunk> chunks = new ArrayList<>();
        chunks.add(chunk);
        
        // 增加一个 CLASS 切片
        CodeChunk classChunk = new CodeChunk();
        classChunk.setTaskId(taskId);
        classChunk.setFilePath("src/main/java/com/demo/controller/UserController.java");
        classChunk.setClassName("UserController");
        classChunk.setChunkType("CLASS");
        classChunk.setContentHash("hashclass");
        classChunk.setStartLine(1);
        classChunk.setEndLine(20);
        classChunk.setTokenEstimate(100);
        classChunk.setStatus("PENDING");
        classChunk.setCreatedAt(LocalDateTime.now());
        chunkMapper.insert(classChunk);
        
        chunks.add(classChunk);

        aiSummaryService.generateDraftDocument(taskId, chunks, "测试提示词");

        // 验证 Draft 是否正确产生
        List<KnowledgeDraft> drafts = knowledgeDraftMapper.selectList(null);
        Assertions.assertFalse(drafts.isEmpty());
        
        // 应该产生了 DemoModule 的模块草稿 (通过多优先级包名路由匹配)
        boolean hasDemoModule = false;
        for (KnowledgeDraft d : drafts) {
            if (d.getModuleName().contains("DemoModule")) {
                hasDemoModule = true;
            }
        }
        Assertions.assertTrue(hasDemoModule);
    }

    @Test
    public void testSensitiveInfoFilter() {
        AiSummaryServiceImpl impl = (AiSummaryServiceImpl) aiSummaryService;
        String input = "my password = 'secret123' and bearer 123456abcdef and jdbc:mysql://localhost:3306/db?user=root&password=mysqlpwd and http://192.168.1.100/api";
        String filtered = impl.filterSensitiveInfo(input);
        Assertions.assertTrue(filtered.contains("password=***") || filtered.contains("password = ***") || filtered.contains("pwd=***"));
        Assertions.assertTrue(filtered.contains("bearer ***"));
        Assertions.assertTrue(filtered.contains("http://***"));
    }

    @Test
    public void testTokenQuotaLimits() {
        Long taskId = 888L;
        DecompileTask task = new DecompileTask();
        task.setId(taskId);
        task.setSystemId(2L);
        task.setRepositoryId(2L);
        task.setStatus("PENDING");
        task.setType("INITIAL");
        task.setProgress(0);
        taskMapper.insert(task);

        CodeChunk chunk = new CodeChunk();
        chunk.setTaskId(taskId);
        chunk.setFilePath("src/main/java/com/demo/entity/User.java");
        chunk.setClassName("User");
        chunk.setChunkType("CLASS");
        chunk.setContentHash("hash123");
        chunk.setStartLine(1);
        chunk.setEndLine(10);
        chunk.setStatus("PENDING");
        chunk.setCreatedAt(LocalDateTime.now());
        chunkMapper.insert(chunk);

        TokenUsageAudit audit = new TokenUsageAudit();
        audit.setSystemId(2L);
        audit.setTaskId(taskId);
        audit.setModelName("MiniMax-M3");
        audit.setInputTokens(120000); 
        audit.setOutputTokens(1000);
        audit.setTotalTokens(121000);
        audit.setCost(java.math.BigDecimal.ZERO);
        audit.setType("INITIAL");
        audit.setStatus(1);
        audit.setCreatedAt(LocalDateTime.now());
        tokenUsageAuditMapper.insert(audit);

        Assertions.assertThrows(com.company.codeinsight.common.exception.BusinessException.class, () -> {
            aiSummaryService.summarizeChunk(taskId, chunk.getId(), "测试提示词", "MiniMax-M3");
        });
    }

    @Test
    public void testModuleRouting() {
        AiSummaryServiceImpl impl = (AiSummaryServiceImpl) aiSummaryService;
        String m1 = impl.routeModuleForFile(999L, 999L, "src/main/java/com/company/codeinsight/modules/system/entity/SystemApplication.java", null);
        Assertions.assertEquals("SystemModule", m1);
        
        String m2 = impl.routeModuleForFile(999L, 999L, "src/main/java/com/demo/controller/UserController.java", null);
        Assertions.assertEquals("DemoModule", m2);
    }
}
