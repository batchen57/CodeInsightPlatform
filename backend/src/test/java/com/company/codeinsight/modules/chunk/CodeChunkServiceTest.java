package com.company.codeinsight.modules.chunk;

import com.company.codeinsight.modules.chunk.entity.CodeChunk;
import com.company.codeinsight.modules.chunk.service.CodeChunkService;
import com.company.codeinsight.modules.scanner.entity.CodeFileSnapshot;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CodeChunkServiceTest {

    @Autowired
    private CodeChunkService codeChunkService;

    @Test
    public void testChunkAndEstimate() throws Exception {
        Long taskId = 888L;
        File tempFile = File.createTempFile("UserController", ".java");
        tempFile.deleteOnExit();

        Files.writeString(tempFile.toPath(), """
package com.demo.controller;

import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
    @GetMapping("/users")
    public String getUsers() {
        return "users";
    }
}
""");

        codeChunkService.chunkAndEstimate(taskId, List.of(snapshot(taskId, tempFile, 10)));

        List<CodeChunk> chunks = codeChunkService.getChunksByTaskId(taskId);
        Assertions.assertFalse(chunks.isEmpty());
        Assertions.assertTrue(chunks.stream().anyMatch(chunk -> "FILE".equals(chunk.getChunkType())));
        Assertions.assertTrue(chunks.stream().anyMatch(chunk -> "CLASS".equals(chunk.getChunkType())));
        Assertions.assertTrue(chunks.stream().anyMatch(chunk -> "METHOD".equals(chunk.getChunkType()) && "getUsers".equals(chunk.getMethodName())));
    }

    @Test
    public void testLargeMethodIsSplitIntoPartChunks() throws Exception {
        Long taskId = 889L;
        File tempFile = File.createTempFile("LargeController", ".java");
        tempFile.deleteOnExit();

        List<String> lines = new ArrayList<>();
        lines.add("package com.demo.controller;");
        lines.add("public class LargeController {");
        lines.add("    public String renderReport() {");
        lines.add("        String value = \"start\";");
        for (int i = 0; i < 120; i++) {
            lines.add("        value = value + \"line-" + i + "-abcdefghijklmnopqrstuvwxyz\";");
        }
        lines.add("        return value;");
        lines.add("    }");
        lines.add("}");
        Files.write(tempFile.toPath(), lines);

        codeChunkService.chunkAndEstimate(taskId, List.of(snapshot(taskId, tempFile, lines.size())));

        List<CodeChunk> chunks = codeChunkService.getChunksByTaskId(taskId);
        Assertions.assertTrue(chunks.stream().anyMatch(chunk -> "FILE_PART".equals(chunk.getChunkType())));
        Assertions.assertTrue(chunks.stream().anyMatch(chunk -> "CLASS_PART".equals(chunk.getChunkType())));
        Assertions.assertTrue(chunks.stream().anyMatch(chunk -> "METHOD_PART".equals(chunk.getChunkType())));
        Assertions.assertTrue(chunks.stream()
                .filter(chunk -> chunk.getChunkType().endsWith("_PART"))
                .allMatch(chunk -> chunk.getTokenEstimate() <= 1200));
    }

    @Test
    public void testFailedChunkCanBeMarkedAndRetried() throws Exception {
        Long taskId = 890L;
        CodeFileSnapshot snapshot = new CodeFileSnapshot();
        snapshot.setTaskId(taskId);
        snapshot.setFilePath("src/main/java/com/demo/Missing.java");
        snapshot.setFileType("java");
        snapshot.setLineCount(0);
        snapshot.setFileHash("missing");
        snapshot.setContentUri(new File("missing-file.java").toURI().toString());
        snapshot.setCreatedAt(LocalDateTime.now());

        codeChunkService.chunkAndEstimate(taskId, List.of(snapshot));
        CodeChunk failed = codeChunkService.getChunksByTaskId(taskId).get(0);
        Assertions.assertEquals("FAILED", failed.getStatus());
        Assertions.assertTrue(failed.getErrorReason().contains("does not exist"));

        codeChunkService.retryChunk(failed.getId());
        CodeChunk retried = codeChunkService.getChunksByTaskId(taskId).get(0);
        Assertions.assertEquals("PENDING", retried.getStatus());
        Assertions.assertNull(retried.getErrorReason());

        codeChunkService.markChunkFailed(retried.getId(), "manual failure");
        CodeChunk markedFailed = codeChunkService.getChunksByTaskId(taskId).get(0);
        Assertions.assertEquals("FAILED", markedFailed.getStatus());
        Assertions.assertEquals("manual failure", markedFailed.getErrorReason());
    }

    private CodeFileSnapshot snapshot(Long taskId, File file, int lineCount) {
        CodeFileSnapshot snapshot = new CodeFileSnapshot();
        snapshot.setTaskId(taskId);
        snapshot.setFilePath("src/main/java/com/demo/controller/" + file.getName());
        snapshot.setFileType("java");
        snapshot.setLineCount(lineCount);
        snapshot.setFileHash("hash-" + file.getName());
        snapshot.setContentUri(file.toURI().toString());
        snapshot.setCreatedAt(LocalDateTime.now());
        return snapshot;
    }
}
