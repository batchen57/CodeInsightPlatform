package com.company.codeinsight.modules.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.codeinsight.modules.draft.entity.DraftWorkspace;
import com.company.codeinsight.modules.draft.entity.KnowledgeDraft;
import com.company.codeinsight.modules.draft.mapper.DraftWorkspaceMapper;
import com.company.codeinsight.modules.draft.mapper.KnowledgeDraftMapper;
import com.company.codeinsight.modules.knowledge.entity.KnowledgeVersion;
import com.company.codeinsight.modules.knowledge.service.KnowledgeService;
import com.company.codeinsight.modules.repository.entity.CodeRepository;
import com.company.codeinsight.modules.repository.service.CodeRepositoryService;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.mapper.DecompileTaskMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class KnowledgeServiceTest {

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private DecompileTaskMapper taskMapper;

    @Autowired
    private CodeRepositoryService repositoryService;

    @Autowired
    private DraftWorkspaceMapper workspaceMapper;

    @Autowired
    private KnowledgeDraftMapper draftMapper;

    @Test
    public void testKnowledgeVersionAndPush() throws Exception {
        Long taskId = 666L;

        // 1. 创建 Repository 与 Task
        CodeRepository repo = new CodeRepository();
        repo.setSystemId(1L);
        repo.setGitUrl("https://github.com/dummy/repo.git");
        repo.setBranch("main");
        repo.setLastCommitId("mockcommit123");
        repositoryService.save(repo);

        DecompileTask task = new DecompileTask();
        task.setId(taskId);
        task.setSystemId(1L);
        task.setRepositoryId(repo.getId());
        task.setStatus("PENDING");
        task.setType("INITIAL");
        task.setProgress(0);
        taskMapper.insert(task);

        // 2. 创建草稿工作区和草稿文件
        DraftWorkspace ws = new DraftWorkspace();
        ws.setTaskId(taskId);
        ws.setSystemId(1L);
        ws.setRepositoryId(repo.getId());
        ws.setStatus("ACTIVE");
        ws.setCreatedAt(LocalDateTime.now());
        ws.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.insert(ws);

        File tempFile = File.createTempFile("MockDraft", ".md");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), "# 测试归纳内容");

        KnowledgeDraft draft = new KnowledgeDraft();
        draft.setWorkspaceId(ws.getId());
        draft.setFilePath("MockDraft.md");
        draft.setModuleName("订单模块");
        draft.setContentUri(tempFile.toURI().toString());
        draft.setStatus("CONFIRMED");
        draft.setHash("hashabc");
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draftMapper.insert(draft);

        // 3. 创建版本
        KnowledgeVersion version = knowledgeService.createVersion(taskId, "v1.0.0", "Tester");
        Assertions.assertNotNull(version);
        Assertions.assertEquals("v1.0.0", version.getVersionNum());
        Assertions.assertEquals("DRAFT", version.getStatus());

        // 4. 模拟 Git 推送
        knowledgeService.pushToGit(version.getId());
        
        // 推送后状态应该变成 PUSHED
        KnowledgeVersion pushedVersion = versionMapperSelect(version.getId());
        Assertions.assertEquals("PUSHED", pushedVersion.getStatus());
        Assertions.assertNotNull(pushedVersion.getTargetCommit());

        // 5. 导出 ZIP
        byte[] zipBytes = knowledgeService.exportZip(version.getId());
        Assertions.assertNotNull(zipBytes);
        Assertions.assertTrue(zipBytes.length > 0);
    }

    @Autowired
    private com.company.codeinsight.modules.knowledge.mapper.KnowledgeVersionMapper testVersionMapper;

    private KnowledgeVersion versionMapperSelect(Long id) {
        return testVersionMapper.selectById(id);
    }

    @Test
    public void testPushToGitValidations() throws Exception {
        Long taskId = 999L;

        CodeRepository repo = new CodeRepository();
        repo.setSystemId(1L);
        repo.setGitUrl("https://github.com/dummy/repo.git");
        repo.setBranch("main");
        repo.setLastCommitId("mockcommit123");
        repositoryService.save(repo);

        DecompileTask task = new DecompileTask();
        task.setId(taskId);
        task.setSystemId(1L);
        task.setRepositoryId(repo.getId());
        task.setStatus("PENDING");
        task.setType("INITIAL");
        task.setProgress(0);
        taskMapper.insert(task);

        DraftWorkspace ws = new DraftWorkspace();
        ws.setTaskId(taskId);
        ws.setSystemId(1L);
        ws.setRepositoryId(repo.getId());
        ws.setStatus("ACTIVE");
        ws.setCreatedAt(LocalDateTime.now());
        ws.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.insert(ws);

        File tempFile = File.createTempFile("MockDraft", ".md");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), "# 待确认内容\n- [ ] 并发事务锁机制");

        KnowledgeDraft draft = new KnowledgeDraft();
        draft.setWorkspaceId(ws.getId());
        draft.setFilePath("MockDraft.md");
        draft.setModuleName("订单模块");
        draft.setContentUri(tempFile.toURI().toString());
        draft.setStatus("CONFIRMED");
        draft.setHash("hashabc");
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draftMapper.insert(draft);

        KnowledgeVersion version = knowledgeService.createVersion(taskId, "v2.0.0", "Tester");
        
        Assertions.assertThrows(com.company.codeinsight.common.exception.BusinessException.class, () -> {
            knowledgeService.pushToGit(version.getId());
        });

        draft.setStatus("AI_GENERATED");
        draftMapper.updateById(draft);
        Files.writeString(tempFile.toPath(), "# 已解决待确认项");
        
        Assertions.assertThrows(com.company.codeinsight.common.exception.BusinessException.class, () -> {
            knowledgeService.pushToGit(version.getId());
        });
    }
}
