package com.company.codeinsight.modules.knowledge.browse;

import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.common.storage.StorageProperties;
import com.company.codeinsight.common.storage.TaskWorkspacePaths;
import com.company.codeinsight.modules.knowledge.entity.KnowledgeVersion;
import com.company.codeinsight.modules.knowledge.mapper.KnowledgeVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * NAS releases 目录数据源：从 {@code {basePath}/releases/{sysId}/{repoId}/{versionNum}/} 读索引/清单文件。
 * <p>找不到版本或 releases 目录不存在时自动回退到 {@link TempReposKnowledgeBrowseSource}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "code-insight.storage.mode", havingValue = "shared")
public class NasKnowledgeBrowseSource implements KnowledgeBrowseSource {

    static final long SIZE_LIMIT_BYTES = 5L * 1024 * 1024;

    private final StorageProperties storageProperties;
    private final KnowledgeVersionMapper versionMapper;
    private final TaskWorkspacePaths taskWorkspacePaths;

    @Override
    public List<IndexFileEntry> listIndexFiles(Long taskId) {
        KnowledgeVersion v = findVersion(taskId);
        if (v == null) return listFromTempRepos(taskId);

        Path releaseDir = storageProperties.releaseDir(v.getSystemId(), v.getRepositoryId(), v.getVersionNum());
        if (!Files.isDirectory(releaseDir)) return listFromTempRepos(taskId);

        List<IndexFileEntry> out = new ArrayList<>();
        walkForEntries(releaseDir.resolve("index"), "INDEX", out);
        walkForEntries(releaseDir.resolve("modules"), "INDEX", out);
        walkForEntries(releaseDir.resolve("meta"), "MANIFEST", out);
        out.sort((a, b) -> b.updatedAt().compareTo(a.updatedAt()));
        return out;
    }

    @Override
    public String readIndexFile(Long taskId, String filePath) {
        KnowledgeVersion v = findVersion(taskId);
        if (v == null) return readFromTempRepos(taskId, filePath);

        Path releaseDir = storageProperties.releaseDir(v.getSystemId(), v.getRepositoryId(), v.getVersionNum());
        Path resolved = releaseDir.resolve(filePath).normalize();
        if (!resolved.startsWith(releaseDir)) throw new BusinessException("非法路径（越界访问）：" + filePath);
        if (!Files.exists(resolved)) return readFromTempRepos(taskId, filePath);
        if (!Files.isRegularFile(resolved)) throw new BusinessException("路径不是普通文件：" + filePath);

        try {
            long size = Files.size(resolved);
            if (size > SIZE_LIMIT_BYTES)
                throw new BusinessException("文件过大（" + size + " 字节），超过 5 MB 上限");
            return Files.readString(resolved);
        } catch (IOException e) {
            throw new BusinessException("读取文件失败：" + filePath + " — " + e.getMessage());
        }
    }

    private KnowledgeVersion findVersion(Long taskId) {
        List<KnowledgeVersion> list = versionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeVersion>()
                        .eq(KnowledgeVersion::getTaskId, taskId)
                        .eq(KnowledgeVersion::getStatus, "PUSHED")
                        .orderByDesc(KnowledgeVersion::getId)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** Fallback：从 temp_repos 读 */
    private List<IndexFileEntry> listFromTempRepos(Long taskId) {
        Path docsRoot = taskWorkspacePaths.taskDocsCodeInsight(taskId);
        if (!Files.isDirectory(docsRoot)) return Collections.emptyList();
        List<IndexFileEntry> out = new ArrayList<>();
        walkForEntries(docsRoot.resolve("index"), "INDEX", out);
        walkForEntries(docsRoot.resolve("modules"), "INDEX", out);
        walkForEntries(docsRoot.resolve("meta"), "MANIFEST", out);
        out.sort((a, b) -> b.updatedAt().compareTo(a.updatedAt()));
        return out;
    }

    /** Fallback：从 temp_repos 读单个文件 */
    private String readFromTempRepos(Long taskId, String filePath) {
        if (filePath.contains("..") || filePath.startsWith("/")) throw new BusinessException("非法路径：" + filePath);
        Path docsRoot = taskWorkspacePaths.taskDocsCodeInsight(taskId);
        Path resolved = docsRoot.resolve(filePath).normalize();
        if (!resolved.startsWith(docsRoot)) throw new BusinessException("非法路径：" + filePath);
        if (!Files.exists(resolved)) throw new BusinessException("索引文件不存在或已被清理");
        try {
            long size = Files.size(resolved);
            if (size > SIZE_LIMIT_BYTES) throw new BusinessException("文件过大");
            return Files.readString(resolved);
        } catch (IOException e) {
            throw new BusinessException("读取失败：" + filePath);
        }
    }

    private void walkForEntries(Path dir, String type, List<IndexFileEntry> out) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                try {
                    String rel = dir.relativize(p).toString().replace('\\', '/');
                    long size = Files.size(p);
                    LocalDateTime mtime = LocalDateTime.ofInstant(
                            Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault());
                    out.add(new IndexFileEntry(rel, type, size, mtime));
                } catch (IOException e) {
                    log.warn("列出索引文件失败：{} — {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("遍历目录失败：{} — {}", dir, e.getMessage());
        }
    }
}
