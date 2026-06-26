package com.company.codeinsight.modules.chunk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.chunk.entity.CodeChunk;
import com.company.codeinsight.modules.chunk.mapper.CodeChunkMapper;
import com.company.codeinsight.modules.chunk.service.CodeChunkService;
import com.company.codeinsight.modules.parser.model.ParsedClassInfo;
import com.company.codeinsight.modules.parser.service.JavaParserService;
import com.company.codeinsight.modules.scanner.entity.CodeFileSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CodeChunkServiceImpl implements CodeChunkService {

    private static final int MAX_CHUNK_LINES = 80;
    private static final int MAX_CHUNK_TOKENS = 1200;
    private static final int MIN_TOKEN_ESTIMATE = 5;

    @Autowired
    private CodeChunkMapper chunkMapper;

    @Autowired
    private JavaParserService javaParserService;

    @Override
    public void chunkAndEstimate(Long taskId, List<CodeFileSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        chunkMapper.delete(new LambdaQueryWrapper<CodeChunk>().eq(CodeChunk::getTaskId, taskId));

        for (CodeFileSnapshot snapshot : snapshots) {
            try {
                File file = new File(URI.create(snapshot.getContentUri()));
                if (!file.exists()) {
                    saveFailedChunk(taskId, snapshot.getFilePath(), "Snapshot file does not exist: " + snapshot.getContentUri());
                    continue;
                }

                List<String> lines = Files.readAllLines(file.toPath());
                String fullContent = String.join("\n", lines);

                saveChunk(taskId, snapshot.getFilePath(), null, null, "FILE",
                        fullContent, 1, lines.size());

                if ("java".equalsIgnoreCase(snapshot.getFileType())) {
                    ParsedClassInfo classInfo = javaParserService.parseFile(file);
                    if (classInfo != null && classInfo.getClassName() != null) {
                        saveChunk(taskId, snapshot.getFilePath(), classInfo.getClassName(), null, "CLASS",
                                fullContent, 1, lines.size());

                        for (ParsedClassInfo.MethodInfo method : classInfo.getMethods()) {
                            int[] range = locateMethodRange(lines, method.getName());
                            int start = range[0];
                            int end = Math.min(range[1], lines.size());
                            String methodCode = joinLines(lines, start, end);

                            saveChunk(taskId, snapshot.getFilePath(), classInfo.getClassName(), method.getName(), "METHOD",
                                    methodCode, start, end);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to chunk file: {}", snapshot.getFilePath(), e);
                saveFailedChunk(taskId, snapshot.getFilePath(), e.getMessage());
            }
        }
    }

    @Override
    public List<CodeChunk> getChunksByTaskId(Long taskId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<CodeChunk>()
                .eq(CodeChunk::getTaskId, taskId)
                .orderByAsc(CodeChunk::getId));
    }

    @Override
    public void markChunkFailed(Long chunkId, String reason) {
        CodeChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null) {
            throw new BusinessException("Code chunk does not exist");
        }
        chunk.setStatus("FAILED");
        chunk.setErrorReason(StringUtils.hasText(reason) ? reason : "Chunk analysis failed");
        chunkMapper.updateById(chunk);
    }

    @Override
    public void retryChunk(Long chunkId) {
        CodeChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null) {
            throw new BusinessException("Code chunk does not exist");
        }
        chunk.setStatus("PENDING");
        chunk.setErrorReason(null);
        chunkMapper.update(null, new LambdaUpdateWrapper<CodeChunk>()
                .eq(CodeChunk::getId, chunkId)
                .set(CodeChunk::getStatus, "PENDING")
                .set(CodeChunk::getErrorReason, null));
    }

    private void saveChunk(Long taskId, String filePath, String className, String methodName, String type,
                           String content, int startLine, int endLine) {
        String normalizedContent = StringUtils.hasText(content) ? content : "";
        int tokenEstimate = estimateTokens(normalizedContent);
        int lineCount = Math.max(1, endLine - startLine + 1);

        if (lineCount > MAX_CHUNK_LINES || tokenEstimate > MAX_CHUNK_TOKENS) {
            saveSplitChunks(taskId, filePath, className, methodName, type, normalizedContent, startLine);
            return;
        }

        insertChunk(taskId, filePath, className, methodName, type, normalizedContent, startLine, endLine, "PENDING", null);
    }

    private void saveSplitChunks(Long taskId, String filePath, String className, String methodName, String type,
                                 String content, int startLine) {
        List<String> lines = List.of(content.split("\\R", -1));
        List<String> partLines = new ArrayList<>();
        int partStartLine = startLine;
        int partIndex = 1;

        for (int i = 0; i < lines.size(); i++) {
            String nextLine = lines.get(i);
            String candidate = partLines.isEmpty()
                    ? nextLine
                    : String.join("\n", partLines) + "\n" + nextLine;
            boolean candidateTooLarge = !partLines.isEmpty()
                    && (partLines.size() + 1 > MAX_CHUNK_LINES || estimateTokens(candidate) > MAX_CHUNK_TOKENS);

            if (candidateTooLarge) {
                String partContent = String.join("\n", partLines);
                int partEndLine = partStartLine + partLines.size() - 1;
                String partMethodName = methodName == null ? null : methodName + "#part" + partIndex;
                insertChunk(taskId, filePath, className, partMethodName, type + "_PART", partContent,
                        partStartLine, partEndLine, "PENDING", null);
                partLines.clear();
                partStartLine = partEndLine + 1;
                partIndex++;
            }

            partLines.add(nextLine);

            boolean lastLine = i == lines.size() - 1;
            if (lastLine) {
                String partContent = String.join("\n", partLines);
                int partEndLine = partStartLine + partLines.size() - 1;
                String partMethodName = methodName == null ? null : methodName + "#part" + partIndex;
                insertChunk(taskId, filePath, className, partMethodName, type + "_PART", partContent,
                        partStartLine, partEndLine, "PENDING", null);
            }
        }
    }

    private void saveFailedChunk(Long taskId, String filePath, String reason) {
        insertChunk(taskId, filePath, null, null, "FILE", "FAILED:" + reason,
                1, 1, "FAILED", StringUtils.hasText(reason) ? reason : "Unknown chunk failure");
    }

    private void insertChunk(Long taskId, String filePath, String className, String methodName, String type,
                             String content, int startLine, int endLine, String status, String errorReason) {
        String normalizedContent = StringUtils.hasText(content) ? content : "";
        CodeChunk chunk = new CodeChunk();
        chunk.setTaskId(taskId);
        chunk.setFilePath(filePath);
        chunk.setClassName(className);
        chunk.setMethodName(methodName);
        chunk.setChunkType(type);
        chunk.setContentHash(DigestUtils.md5DigestAsHex(normalizedContent.getBytes()));
        chunk.setStartLine(startLine);
        chunk.setEndLine(endLine);
        chunk.setTokenEstimate(estimateTokens(normalizedContent));
        chunk.setStatus(status);
        chunk.setErrorReason(errorReason);
        chunk.setCreatedAt(LocalDateTime.now());
        chunkMapper.insert(chunk);
    }

    private String joinLines(List<String> lines, int start, int end) {
        StringBuilder builder = new StringBuilder();
        for (int i = start - 1; i < end && i < lines.size(); i++) {
            builder.append(lines.get(i)).append("\n");
        }
        return builder.toString();
    }

    private int estimateTokens(String content) {
        int tokenEstimate = (int) Math.ceil((StringUtils.hasText(content) ? content.length() : 0) / 3.0);
        return Math.max(MIN_TOKEN_ESTIMATE, tokenEstimate);
    }

    private int[] locateMethodRange(List<String> lines, String methodName) {
        int start = 1;
        int end = 1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.contains(methodName) && line.contains("(")) {
                if (line.endsWith(";")) {
                    return new int[]{i + 1, i + 1};
                }
                start = i + 1;

                int braceCount = 0;
                boolean foundFirstBrace = false;
                for (int j = i; j < lines.size(); j++) {
                    String scanLine = lines.get(j);
                    for (char ch : scanLine.toCharArray()) {
                        if (ch == '{') {
                            braceCount++;
                            foundFirstBrace = true;
                        } else if (ch == '}') {
                            braceCount--;
                        }
                    }
                    if (foundFirstBrace && braceCount <= 0) {
                        end = j + 1;
                        break;
                    }
                }
                if (end < start) {
                    end = Math.min(lines.size(), start + 3);
                }
                break;
            }
        }
        return new int[]{start, end};
    }
}
