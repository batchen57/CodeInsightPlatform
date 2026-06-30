package com.company.codeinsight.modules.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.draft.entity.DraftWorkspace;
import com.company.codeinsight.modules.draft.entity.KnowledgeDraft;
import com.company.codeinsight.modules.draft.enums.DraftStatus;
import com.company.codeinsight.modules.draft.mapper.DraftWorkspaceMapper;
import com.company.codeinsight.modules.draft.mapper.KnowledgeDraftMapper;
import com.company.codeinsight.modules.task.service.DecompileTaskService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;

/**
 * 知识构建任务「新建前置条件」就绪度拦截的集成测试。
 *
 * <p>验证 {@link com.company.codeinsight.modules.task.service.impl.DecompileTaskServiceImpl#validateNoUnconfirmedDrafts(Long, Long)}
 * 在 createInitialTask / createIncrementalTask 入口处生效：</p>
 * <ul>
 *   <li>只要当前系统+仓库下存在任意 DRAFT / EDITING 状态的草稿，新任务创建应抛 BusinessException</li>
 *   <li>当前系统+仓库草稿全部 CONFIRMED / PUSHED / ARCHIVED 时，新任务可正常创建</li>
 *   <li>其他系统+仓库的未确认草稿不影响当前任务创建</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@ActiveProfiles("test")
@Transactional
public class DecompileTaskReadinessTest {

    @Autowired
    private DecompileTaskService decompileTaskService;

    @Autowired
    private KnowledgeDraftMapper draftMapper;

    @Autowired
    private DraftWorkspaceMapper workspaceMapper;

    private Long systemId;
    private Long repositoryId;

    @BeforeEach
    public void setUp() throws Exception {
        // 用一个固定 systemId / repositoryId 便于测试聚焦；
        // 由于 validateTaskSource 会校验 repository 存在性，本测试聚焦于 readiness 校验。
        // 为了避免依赖外部数据，这里仅校验 validateNoUnconfirmedDrafts 的副作用：
        // 当存在未确认草稿时 createInitialTask 应该抛 BusinessException。
        systemId = 9999L;
        repositoryId = 9999L;

        // 清理所有非终态草稿，保证测试起始状态干净
        clearNonTerminalDrafts();
    }

    private void clearNonTerminalDrafts() {
        draftMapper.delete(
                new LambdaQueryWrapper<KnowledgeDraft>()
                        .notIn(KnowledgeDraft::getStatus, java.util.List.of(
                                DraftStatus.CONFIRMED.name(),
                                DraftStatus.PUSHED.name(),
                                DraftStatus.ARCHIVED.name()
                        ))
        );
    }

    /**
     * 存在 DRAFT 草稿时，当前系统+仓库的校验应被拦截。
     */
    @Test
    public void testReadinessGateBlocksWhenDraftPending() throws Exception {
        // 准备一个 DRAFT 状态的草稿（属于 9999/9999）
        DraftWorkspace ws = new DraftWorkspace();
        ws.setTaskId(8001L);
        ws.setSystemId(9999L);
        ws.setRepositoryId(9999L);
        ws.setStatus("ACTIVE");
        ws.setCreatedAt(LocalDateTime.now());
        ws.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.insert(ws);

        File tempFile = File.createTempFile("ReadinessDraft", ".md");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), "# test");

        KnowledgeDraft draft = new KnowledgeDraft();
        draft.setWorkspaceId(ws.getId());
        draft.setFilePath("ReadinessDraft.md");
        draft.setModuleName("就绪度测试模块");
        draft.setContentUri(tempFile.toURI().toString());
        draft.setStatus(DraftStatus.DRAFT.name());
        draft.setHash("hash-readiness");
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draftMapper.insert(draft);

        // 反射调用私有 validateNoUnconfirmedDrafts(systemId, repositoryId)，应该抛 BusinessException
        java.lang.reflect.Method m = com.company.codeinsight.modules.task.service.impl.DecompileTaskServiceImpl.class
                .getDeclaredMethod("validateNoUnconfirmedDrafts", Long.class, Long.class);
        m.setAccessible(true);
        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> {
            try {
                m.invoke(decompileTaskService, 9999L, 9999L);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw (BusinessException) e.getCause();
            }
        });
        Assertions.assertTrue(ex.getMessage().contains("草稿未确认"));
    }

    /**
     * 当前系统+仓库草稿全部 CONFIRMED 时，校验方法应直接通过不抛错。
     */
    @Test
    public void testReadinessGatePassesWhenAllConfirmed() throws Exception {
        // 先清空所有非终态草稿
        clearNonTerminalDrafts();

        // 准备一个工作区与一条 CONFIRMED 草稿（属于 9999/9999）
        DraftWorkspace ws = new DraftWorkspace();
        ws.setTaskId(8002L);
        ws.setSystemId(9999L);
        ws.setRepositoryId(9999L);
        ws.setStatus("COMPLETED");
        ws.setCreatedAt(LocalDateTime.now());
        ws.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.insert(ws);

        File tempFile = File.createTempFile("ConfirmedDraft", ".md");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), "# confirmed");

        KnowledgeDraft draft = new KnowledgeDraft();
        draft.setWorkspaceId(ws.getId());
        draft.setFilePath("ConfirmedDraft.md");
        draft.setModuleName("已确认模块");
        draft.setContentUri(tempFile.toURI().toString());
        draft.setStatus(DraftStatus.CONFIRMED.name());
        draft.setHash("hash-confirmed");
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draftMapper.insert(draft);

        // 反射调用私有方法(9999, 9999)，应直接通过
        java.lang.reflect.Method m = com.company.codeinsight.modules.task.service.impl.DecompileTaskServiceImpl.class
                .getDeclaredMethod("validateNoUnconfirmedDrafts", Long.class, Long.class);
        m.setAccessible(true);
        Assertions.assertDoesNotThrow(() -> {
            try {
                m.invoke(decompileTaskService, 9999L, 9999L);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    /**
     * 其他系统+仓库的未确认草稿不影响当前系统+仓库的任务创建。
     * 验证作用域收窄正确。
     */
    @Test
    public void testReadinessGateIgnoresOtherSystemsDrafts() throws Exception {
        // 先清空所有非终态草稿
        clearNonTerminalDrafts();

        // 在其他系统(8888/8888)下创建一个 DRAFT 草稿
        DraftWorkspace otherWs = new DraftWorkspace();
        otherWs.setTaskId(8003L);
        otherWs.setSystemId(8888L);
        otherWs.setRepositoryId(8888L);
        otherWs.setStatus("ACTIVE");
        otherWs.setCreatedAt(LocalDateTime.now());
        otherWs.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.insert(otherWs);

        File tempFile = File.createTempFile("OtherDraft", ".md");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), "# other system draft");

        KnowledgeDraft otherDraft = new KnowledgeDraft();
        otherDraft.setWorkspaceId(otherWs.getId());
        otherDraft.setFilePath("OtherDraft.md");
        otherDraft.setModuleName("其他系统模块");
        otherDraft.setContentUri(tempFile.toURI().toString());
        otherDraft.setStatus(DraftStatus.DRAFT.name());
        otherDraft.setHash("hash-other");
        otherDraft.setCreatedAt(LocalDateTime.now());
        otherDraft.setUpdatedAt(LocalDateTime.now());
        draftMapper.insert(otherDraft);

        // 当前系统(9999/9999)下没有任何草稿，校验应直接通过
        java.lang.reflect.Method m = com.company.codeinsight.modules.task.service.impl.DecompileTaskServiceImpl.class
                .getDeclaredMethod("validateNoUnconfirmedDrafts", Long.class, Long.class);
        m.setAccessible(true);
        Assertions.assertDoesNotThrow(() -> {
            try {
                m.invoke(decompileTaskService, 9999L, 9999L);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}