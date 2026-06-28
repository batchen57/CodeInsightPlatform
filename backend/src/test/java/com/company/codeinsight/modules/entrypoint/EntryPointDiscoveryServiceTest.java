package com.company.codeinsight.modules.entrypoint;

import com.company.codeinsight.modules.callchain.service.MethodCallService;
import com.company.codeinsight.modules.entrypoint.model.EntryPoint;
import com.company.codeinsight.modules.entrypoint.model.EntryPointConfig;
import com.company.codeinsight.modules.entrypoint.service.EntryPointDiscoveryService;
import com.company.codeinsight.modules.entrypoint.service.impl.EntryPointDiscoveryServiceImpl;
import com.company.codeinsight.modules.parser.model.ParsedClassInfo;
import com.company.codeinsight.modules.parser.service.JavaParserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 入口识别服务测试（mock 掉 JavaParserService / MethodCallService）
 */
public class EntryPointDiscoveryServiceTest {

    private JavaParserService javaParserService;
    private MethodCallService methodCallService;
    private EntryPointDiscoveryService service;

    @BeforeEach
    void setUp() {
        javaParserService = mock(JavaParserService.class);
        methodCallService = mock(MethodCallService.class);
        service = new EntryPointDiscoveryServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "javaParserService", javaParserService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "methodCallService", methodCallService);
    }

    private ParsedClassInfo buildClass(String className, String type, boolean hasMain, String... annotations) {
        ParsedClassInfo info = new ParsedClassInfo();
        info.setClassName(className);
        info.setPackageName("com.demo");
        info.setType(type);
        info.setHasMainMethod(hasMain);
        info.setAnnotations(Arrays.asList(annotations));
        return info;
    }

    @Test
    public void testRestControllerDetected() {
        ParsedClassInfo ctl = buildClass("UserController", "CONTROLLER", false, "RestController");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(ctl));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        List<EntryPoint> entries = service.discoverEntries(1L, new File("."));
        Assertions.assertEquals(1, entries.size());
        Assertions.assertEquals("CONTROLLER", entries.get(0).getEntryType());
        Assertions.assertEquals("com.demo.UserController", entries.get(0).getClassName());
    }

    @Test
    public void testScheduledJobDetected() {
        ParsedClassInfo job = buildClass("DailySyncJob", "JOB", false, "Component", "Scheduled");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(job));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        List<EntryPoint> entries = service.discoverEntries(1L, new File("."));
        Assertions.assertEquals(1, entries.size());
        Assertions.assertEquals("SCHEDULED_JOB", entries.get(0).getEntryType());
    }

    @Test
    public void testMqListenerDetected() {
        ParsedClassInfo mq = buildClass("OrderConsumer", "MESSAGE_LISTENER", false, "Component", "RabbitListener");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(mq));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        List<EntryPoint> entries = service.discoverEntries(1L, new File("."));
        Assertions.assertEquals(1, entries.size());
        Assertions.assertEquals("MQ_LISTENER", entries.get(0).getEntryType());
    }

    @Test
    public void testMainMethodDetectedAsApplication() {
        ParsedClassInfo app = buildClass("CodeInsightApplication", "APPLICATION", true, "SpringBootApplication");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(app));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        List<EntryPoint> entries = service.discoverEntries(1L, new File("."));
        Assertions.assertEquals(1, entries.size());
        Assertions.assertEquals("APPLICATION", entries.get(0).getEntryType());
    }

    @Test
    public void testDuplicateDedup() {
        ParsedClassInfo a = buildClass("DupController", "CONTROLLER", false, "RestController");
        ParsedClassInfo b = buildClass("DupController", "CONTROLLER", false, "RestController");
        when(javaParserService.parseDirectory(any())).thenReturn(Arrays.asList(a, b));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        List<EntryPoint> entries = service.discoverEntries(1L, new File("."));
        Assertions.assertEquals(1, entries.size());
    }

    @Test
    public void testCollectReachableSourceIncludesClassFile() throws Exception {
        File root = Files.createTempDirectory("entry-test").toFile();
        root.deleteOnExit();
        File source = new File(root, "src/main/java/com/demo/UserController.java");
        source.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(source)) {
            w.write("package com.demo;\npublic class UserController {}\n");
        }

        ParsedClassInfo info = buildClass("UserController", "CONTROLLER", false, "RestController");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(info));
        // 调用链无记录 → 直接读物理文件
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        String src = service.collectReachableSource(1L, "com.demo.UserController", root);
        Assertions.assertTrue(src.contains("public class UserController"));
    }

    // ============================ 配置驱动：include 三类规则 ============================

    @Test
    public void testIncludeAnnotationsHit() {
        ParsedClassInfo ctl = buildClass("UserController", "UNKNOWN", false, "RestController");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(ctl));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        EntryPointConfig cfg = new EntryPointConfig();
        cfg.setIncludeAnnotations(Arrays.asList("RestController"));
        List<EntryPoint> entries = service.discoverEntries(1L, new File("."), cfg);
        Assertions.assertEquals(1, entries.size());
        Assertions.assertEquals("com.demo.UserController", entries.get(0).getClassName());
    }

    @Test
    public void testIncludeClasspathsAnt() {
        ParsedClassInfo ctl = buildClass("UserController", "UNKNOWN", false);
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(ctl));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        EntryPointConfig cfg = new EntryPointConfig();
        cfg.setIncludeClasspaths(Arrays.asList("com.demo.controller.*"));
        List<EntryPoint> entries = service.discoverEntries(1L, new File("."), cfg);
        Assertions.assertEquals(1, entries.size());
    }

    @Test
    public void testIncludeExtends() {
        ParsedClassInfo child = buildClass("MyEntry", "UNKNOWN", false);
        child.setExtendsClass("com.demo.BaseEntry");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(child));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        EntryPointConfig cfg = new EntryPointConfig();
        cfg.setIncludeExtends(Arrays.asList("com.demo.BaseEntry"));
        List<EntryPoint> entries = service.discoverEntries(1L, new File("."), cfg);
        Assertions.assertEquals(1, entries.size());
    }

    // ============================ 配置驱动：exclude 三类规则 ============================

    @Test
    public void testExcludeClasspath() {
        ParsedClassInfo ctl = buildClass("TestController", "UNKNOWN", false, "RestController");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(ctl));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        EntryPointConfig cfg = new EntryPointConfig();
        cfg.setIncludeAnnotations(Arrays.asList("RestController"));
        cfg.setExcludeClasspaths(Arrays.asList("*.TestController"));
        List<EntryPoint> entries = service.discoverEntries(1L, new File("."), cfg);
        Assertions.assertEquals(0, entries.size());
    }

    @Test
    public void testExcludePackage() {
        ParsedClassInfo ctl = buildClass("ConfigController", "UNKNOWN", false, "RestController");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(ctl));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        EntryPointConfig cfg = new EntryPointConfig();
        cfg.setIncludeAnnotations(Arrays.asList("RestController"));
        cfg.setExcludePackages(Arrays.asList("com.demo.config"));
        List<EntryPoint> entries = service.discoverEntries(1L, new File("."), cfg);
        Assertions.assertEquals(0, entries.size());
    }

    @Test
    public void testExcludeAnnotation() {
        ParsedClassInfo ctl = buildClass("UserController", "UNKNOWN", false, "RestController", "Internal");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(ctl));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        EntryPointConfig cfg = new EntryPointConfig();
        cfg.setIncludeAnnotations(Arrays.asList("RestController"));
        cfg.setExcludeAnnotations(Arrays.asList("Internal"));
        List<EntryPoint> entries = service.discoverEntries(1L, new File("."), cfg);
        Assertions.assertEquals(0, entries.size());
    }

    // ============================ "或"逻辑与默认行为兜底 ============================

    @Test
    public void testMultipleIncludeRulesOrLogic() {
        ParsedClassInfo a = buildClass("A", "UNKNOWN", false, "RestController");
        ParsedClassInfo b = buildClass("B", "UNKNOWN", false);
        ParsedClassInfo c = buildClass("C", "UNKNOWN", false);
        c.setExtendsClass("com.demo.BaseEntry");
        when(javaParserService.parseDirectory(any())).thenReturn(Arrays.asList(a, b, c));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        EntryPointConfig cfg = new EntryPointConfig();
        cfg.setIncludeClasspaths(Arrays.asList("com.demo.B"));
        List<EntryPoint> entries = service.discoverEntries(1L, new File("."), cfg);
        Assertions.assertEquals(1, entries.size());
        Assertions.assertEquals("com.demo.B", entries.get(0).getClassName());
    }

    @Test
    public void testMultipleExcludeRulesOrLogic() {
        ParsedClassInfo a = buildClass("A", "UNKNOWN", false, "RestController");
        ParsedClassInfo b = buildClass("B", "UNKNOWN", false, "RestController", "Internal");
        when(javaParserService.parseDirectory(any())).thenReturn(Arrays.asList(a, b));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        EntryPointConfig cfg = new EntryPointConfig();
        cfg.setIncludeAnnotations(Arrays.asList("RestController"));
        cfg.setExcludeAnnotations(Arrays.asList("Internal"));
        List<EntryPoint> entries = service.discoverEntries(1L, new File("."), cfg);
        Assertions.assertEquals(1, entries.size());
        Assertions.assertEquals("com.demo.A", entries.get(0).getClassName());
    }

    @Test
    public void testConfigNullFallsBackToDefault() {
        ParsedClassInfo ctl = buildClass("UserController", "CONTROLLER", false, "RestController");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(ctl));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        List<EntryPoint> entries = service.discoverEntries(1L, new File("."), null);
        Assertions.assertEquals(1, entries.size());
        Assertions.assertEquals("CONTROLLER", entries.get(0).getEntryType());
    }

    @Test
    public void testConfigAllIncludeEmptyFallsBackToDefault() {
        ParsedClassInfo ctl = buildClass("UserController", "CONTROLLER", false, "RestController");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(ctl));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        EntryPointConfig cfg = new EntryPointConfig();
        cfg.setExcludeClasspaths(Arrays.asList("never.match.*"));
        List<EntryPoint> entries = service.discoverEntries(1L, new File("."), cfg);
        Assertions.assertEquals(1, entries.size());
        Assertions.assertEquals("CONTROLLER", entries.get(0).getEntryType());
    }

    @Test
    public void testReadEntrySourceUsesMultiModulePath() throws Exception {
        File root = Files.createTempDirectory("entry-multi").toFile();
        root.deleteOnExit();
        File source = new File(root, "accounting-service/src/main/java/net/demo/AccountsController.java");
        source.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(source)) {
            w.write("package net.demo;\n@RestController\npublic class AccountsController {}\n");
        }

        ParsedClassInfo info = buildClass("AccountsController", "CONTROLLER", false, "RestController");
        info.setPackageName("net.demo");
        info.setSourceRelativePath("accounting-service/src/main/java/net/demo/AccountsController.java");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(info));
        when(javaParserService.parseFile(any())).thenReturn(info);
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        List<EntryPoint> entries = service.discoverEntries(1L, root);
        Assertions.assertEquals(1, entries.size());
        Assertions.assertEquals("accounting-service/src/main/java/net/demo/AccountsController.java", entries.get(0).getFilePath());

        String content = service.readEntrySource(root, entries.get(0), null);
        Assertions.assertTrue(content.contains("AccountsController"));
    }

    @Test
    public void testCollectReachableSourceRespectsExclude() throws Exception {
        File root = Files.createTempDirectory("entry-test-excl").toFile();
        root.deleteOnExit();
        File src = new File(root, "src/main/java/com/demo/UserController.java");
        src.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(src)) {
            w.write("package com.demo;\npublic class UserController {}\n");
        }

        ParsedClassInfo info = buildClass("UserController", "CONTROLLER", false, "RestController");
        when(javaParserService.parseDirectory(any())).thenReturn(Collections.singletonList(info));
        when(methodCallService.listByTaskId(any())).thenReturn(Collections.emptyList());

        EntryPointConfig cfg = new EntryPointConfig();
        cfg.setExcludeClasspaths(Arrays.asList("com.demo.UserController"));
        String result = service.collectReachableSource(1L, "com.demo.UserController", root, cfg);
        Assertions.assertEquals("", result);
    }
}