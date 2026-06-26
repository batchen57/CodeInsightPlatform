package com.company.codeinsight.modules.draft;

import com.company.codeinsight.modules.draft.entity.*;
import com.company.codeinsight.modules.draft.mapper.DraftWorkspaceMapper;
import com.company.codeinsight.modules.draft.mapper.KnowledgeDraftMapper;
import com.company.codeinsight.modules.draft.service.DraftService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@ActiveProfiles("test")
@Transactional
public class DraftServiceTest {

    @Autowired
    private DraftService draftService;

    @Autowired
    private DraftWorkspaceMapper workspaceMapper;

    @Autowired
    private KnowledgeDraftMapper draftMapper;

    @Test
    public void testDraftLifecycle() throws Exception {
        // 1. 创建工作区和草稿记录
        DraftWorkspace ws = new DraftWorkspace();
        ws.setTaskId(100L);
        ws.setSystemId(1L);
        ws.setRepositoryId(1L);
        ws.setStatus("ACTIVE");
        ws.setCreatedAt(LocalDateTime.now());
        ws.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.insert(ws);

        File tempFile = File.createTempFile("MockDraft", ".md");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), "# 原始设计");

        KnowledgeDraft draft = new KnowledgeDraft();
        draft.setWorkspaceId(ws.getId());
        draft.setFilePath("MockDraft.md");
        draft.setModuleName("核心逻辑层");
        draft.setContentUri(tempFile.toURI().toString());
        draft.setStatus("AI_GENERATED");
        draft.setHash("hash111");
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draftMapper.insert(draft);

        // 2. 验证读取原始内容
        String original = draftService.getDraftContent(draft.getId());
        Assertions.assertEquals("# 原始设计", original);

        // 3. 验证自动保存
        draftService.autoSaveDraft(draft.getId(), "# 自动保存的设计", "Author");
        String autosave = draftService.getDraftContent(draft.getId());
        Assertions.assertEquals("# 自动保存的设计", autosave);

        // 4. 验证手动保存并清除自动保存
        draftService.saveDraft(draft.getId(), "# 最终保存的设计", "Author", "手工修改");
        String saved = draftService.getDraftContent(draft.getId());
        Assertions.assertEquals("# 最终保存的设计", saved);

        // 验证修订记录
        List<DraftRevision> revisions = draftService.getRevisions(draft.getId());
        Assertions.assertEquals(1, revisions.size());
        Assertions.assertTrue(revisions.get(0).getRemark().contains("手工修改"));

        // 5. 验证驳回和评审意见
        draftService.rejectDraft(draft.getId(), "Reviewer", "写得不够通俗");
        KnowledgeDraft rejectedDraft = draftService.getDraftById(draft.getId());
        Assertions.assertEquals("REJECTED", rejectedDraft.getStatus());

        List<DraftReviewComment> comments = draftService.getComments(draft.getId());
        Assertions.assertEquals(1, comments.size());
        Assertions.assertEquals("写得不够通俗", comments.get(0).getComment());

        // 6. 验证确认草稿
        draftService.confirmDraft(draft.getId(), "Author");
        KnowledgeDraft confirmedDraft = draftService.getDraftById(draft.getId());
        Assertions.assertEquals("CONFIRMED", confirmedDraft.getStatus());

        // 工作区应该因为所有草稿已确认自动转为 COMPLETED
        DraftWorkspace updatedWs = workspaceMapper.selectById(ws.getId());
        Assertions.assertEquals("COMPLETED", updatedWs.getStatus());
    }
}
