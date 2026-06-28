package com.company.codeinsight.modules.knowledge;

import com.company.codeinsight.modules.draft.entity.KnowledgeDraft;
import com.company.codeinsight.modules.hierarchy.model.FunctionDto;
import com.company.codeinsight.modules.hierarchy.model.ModuleDto;
import com.company.codeinsight.modules.hierarchy.model.ModuleHierarchy;
import com.company.codeinsight.modules.hierarchy.model.SubModuleDto;
import com.company.codeinsight.modules.knowledge.service.KnowledgeIndexService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 三级索引生成服务测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class KnowledgeIndexServiceTest {

    @Autowired
    private KnowledgeIndexService knowledgeIndexService;

    @Test
    public void testGenerateThreeLevelIndex() throws Exception {
        Path tempDir = Files.createTempDirectory("idx-test");
        tempDir.toFile().deleteOnExit();
        Path docsPath = tempDir.resolve("docs/code-insight");
        Files.createDirectories(docsPath);

        // 1. 构造 ModuleHierarchy DTO（2 模块、其中 1 模块有 2 子模块、3 功能）
        ModuleHierarchy hierarchy = new ModuleHierarchy();
        hierarchy.setTaskId(9901L);
        hierarchy.setSystemId(1L);

        // 模块 1：用户管理 → 白名单（注册 / 重置）
        ModuleDto userMod = new ModuleDto();
        userMod.setId("m00001");
        userMod.setModuleName("用户管理");
        hierarchy.getModules().put(userMod.getId(), userMod);

        SubModuleDto whiteListSub = new SubModuleDto();
        whiteListSub.setId("s00001");
        whiteListSub.setSubModuleName("白名单");
        userMod.getSubModules().put(whiteListSub.getId(), whiteListSub);

        FunctionDto registerFn = new FunctionDto();
        registerFn.setId("f00001");
        registerFn.setFunctionName("白名单注册");
        registerFn.setClassPaths(new LinkedHashSet<>(List.of("com.demo.UserController")));
        whiteListSub.getFunctions().put(registerFn.getId(), registerFn);

        FunctionDto resetFn = new FunctionDto();
        resetFn.setId("f00002");
        resetFn.setFunctionName("白名单重置");
        resetFn.setClassPaths(new LinkedHashSet<>(List.of("com.demo.UserController", "com.demo.UserService")));
        whiteListSub.getFunctions().put(resetFn.getId(), resetFn);

        // 模块 2：订单管理（无子模块）
        ModuleDto orderMod = new ModuleDto();
        orderMod.setId("m00002");
        orderMod.setModuleName("订单管理");
        hierarchy.getModules().put(orderMod.getId(), orderMod);

        // 2. 构造 KnowledgeDraft（必须有 file_path 才能让索引生成 md 链接）
        List<KnowledgeDraft> drafts = new ArrayList<>();
        drafts.add(buildDraft(9901L, "用户管理", "task_9901/UserManagement.md"));
        drafts.add(buildDraft(9901L, "订单管理", "task_9901/OrderManagement.md"));

        // 3. 调生成
        Path indexPath = knowledgeIndexService.generateModuleIndex(docsPath, hierarchy, drafts);
        Assertions.assertTrue(Files.exists(indexPath));

        // 4. 校验内容
        String content = Files.readString(indexPath);
        Assertions.assertTrue(content.contains("# 模块知识归纳索引"));
        Assertions.assertTrue(content.contains("| 模块 | 子模块 | 功能 | 入口类 | md 链接 |"));
        Assertions.assertTrue(content.contains("用户管理"));
        Assertions.assertTrue(content.contains("白名单"));
        Assertions.assertTrue(content.contains("白名单注册"));
        Assertions.assertTrue(content.contains("白名单重置"));
        Assertions.assertTrue(content.contains("订单管理"));
        // md 链接格式
        Assertions.assertTrue(content.contains("[用户管理](modules/UserManagement.md)"));
        Assertions.assertTrue(content.contains("[订单管理](modules/OrderManagement.md)"));
        // 入口类列
        Assertions.assertTrue(content.contains("com.demo.UserController"));
        // 文档导览
        Assertions.assertTrue(content.contains("[架构概览](architecture-overview.md)"));
    }

    @Test
    public void testFallbackWhenHierarchyEmpty() throws Exception {
        Path tempDir = Files.createTempDirectory("idx-fallback-test");
        tempDir.toFile().deleteOnExit();
        Path docsPath = tempDir.resolve("docs/code-insight");
        Files.createDirectories(docsPath);

        // DTO 为空 → 降级模式
        ModuleHierarchy hierarchy = new ModuleHierarchy();

        List<KnowledgeDraft> drafts = new ArrayList<>();
        drafts.add(buildDraft(9902L, "模块A", "task_9902/A.md"));
        drafts.add(buildDraft(9902L, "模块B", "task_9902/B.md"));

        Path indexPath = knowledgeIndexService.generateModuleIndex(docsPath, hierarchy, drafts);
        String content = Files.readString(indexPath);

        // 降级模式下只输出两列表
        Assertions.assertTrue(content.contains("| 模块 | md 链接 |"));
        Assertions.assertTrue(content.contains("[模块A](modules/A.md)"));
        Assertions.assertTrue(content.contains("[模块B](modules/B.md)"));
    }

    @Test
    public void testEscapeMdForSpecialCharacters() throws Exception {
        Path tempDir = Files.createTempDirectory("idx-escape-test");
        tempDir.toFile().deleteOnExit();
        Path docsPath = tempDir.resolve("docs/code-insight");
        Files.createDirectories(docsPath);

        ModuleHierarchy hierarchy = new ModuleHierarchy();
        ModuleDto m = new ModuleDto();
        m.setId("m00001");
        m.setModuleName("用户|管理");  // 包含 | 字符
        hierarchy.getModules().put(m.getId(), m);

        List<KnowledgeDraft> drafts = new ArrayList<>();
        drafts.add(buildDraft(9903L, "用户|管理", "task_9903/UserManagement.md"));

        Path indexPath = knowledgeIndexService.generateModuleIndex(docsPath, hierarchy, drafts);
        String content = Files.readString(indexPath);

        // | 字符必须被转义成 \\|
        Assertions.assertTrue(content.contains("用户\\|管理"));
        // 不应出现未转义的 | 破坏表格
        Assertions.assertFalse(content.contains("用户|管理\n| ---"));
    }

    private KnowledgeDraft buildDraft(Long workspaceId, String moduleName, String filePath) {
        KnowledgeDraft draft = new KnowledgeDraft();
        draft.setWorkspaceId(workspaceId);
        draft.setModuleName(moduleName);
        draft.setFilePath(filePath);
        draft.setStatus("AI_GENERATED");
        return draft;
    }
}