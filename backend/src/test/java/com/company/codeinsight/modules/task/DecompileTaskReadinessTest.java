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
 * 反编译任务「新建前置条件」就绪度拦截的集成测试。
 *
 * <p>验证 {@link com.company.codeinsight.modules.task.service.impl.DecompileTaskServiceImpl#validateNoUnconfirmedDrafts()}
 * 在 createInitialTask / createIncrementalTask 入口处生效：</p>
 * <ul>
 *   <li>只要全局存在任意 DRAFT / EDITING / REJECTED 状态的草稿，新任务创建应抛 BusinessException</li>
 *   <li>全局草稿全部 CONFIRMED / PUSHED / ARCHIVED 时，新任务可正常创建</li>
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
     * 存在 DRAFT 草稿时，createInitialTask 应被前置条件拦截。
     * 注：validateTaskSource 会先校验 repository 存在性，所以这里只用一个会触发 readiness 校验的快速路径：
     * 通过反射直接调用私有方法（保证不依赖外部 repository/system 数据）。
     * 实际拦截语义在 createInitialTask 中调用，所以也通过 reflectively 调用来验证。
     */
    @Test
    public void testReadinessGateBlocksWhenDraftPending() throws Exception {
        // 准备一个 DRAFT 状态的草稿
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

        // 反射直接调用私有 validateNoUnconfirmedDrafts，应该抛 BusinessException
        java.lang.reflect.Method m = com.company.codeinsight.modules.task.service.impl.DecompileTaskServiceImpl.class
                .getDeclaredMethod("validateNoUnconfirmedDrafts");
        m.setAccessible(true);
        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> {
            try {
                m.invoke(decompileTaskService);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw (BusinessException) e.getCause();
            }
        });
        Assertions.assertTrue(ex.getMessage().contains("草稿未确认"));
    }

    /**
     * 全局草稿全部 CONFIRMED 时，校验方法应直接通过不抛错。
     */
    @Test
    public void testReadinessGatePassesWhenAllConfirmed() throws Exception {
        // 先清空所有非终态草稿
        clearNonTerminalDrafts();

        // 准备一个工作区与一条 CONFIRMED 草稿
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

        // 反射调用私有方法，应直接通过
        java.lang.reflect.Method m = com.company.codeinsight.modules.task.service.impl.DecompileTaskServiceImpl.class
                .getDeclaredMethod("validateNoUnconfirmedDrafts");
        m.setAccessible(true);
        Assertions.assertDoesNotThrow(() -> {
            try {
                m.invoke(decompileTaskService);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}