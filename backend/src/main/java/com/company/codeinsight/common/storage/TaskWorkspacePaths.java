package com.company.codeinsight.common.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一解析任务工作区与 storage 路径，避免硬编码 temp_repos 分散在各模块。
 */
@Component
@RequiredArgsConstructor
public class TaskWorkspacePaths {

    private final StorageProperties storageProperties;

    public File taskProjectDir(long taskId) {
        return new File(storageProperties.taskWorkspaceDir(taskId));
    }

    public Path taskProjectPath(long taskId) {
        return Paths.get(storageProperties.taskWorkspaceDir(taskId));
    }

    public Path taskDocsCodeInsight(long taskId) {
        return taskProjectPath(taskId).resolve("docs/code-insight");
    }

    public String storageLocalPath() {
        return storageProperties.getLocalPath();
    }

    public String workspaceRoot() {
        return storageProperties.getWorkspaceRoot();
    }
}
