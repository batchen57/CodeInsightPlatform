package com.company.codeinsight.common.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 知识文件存储属性（草稿 / 工作区 / 发布产物）。
 *
 * <p>单机开发（{@code mode=local}）：
 * <pre>
 *   code-insight.storage.mode=local
 *   code-insight.storage.local-path=./storage
 *   code-insight.storage.workspace-root=./temp_repos
 * </pre>
 *
 * <p>集群部署（{@code mode=shared}）：
 * <pre>
 *   code-insight.storage.mode=shared
 *   code-insight.storage.base-path=/mnt/knowledge
 *   code-insight.storage.drafts-root=drafts
 *   code-insight.storage.releases-root=releases
 *   code-insight.storage.workspace-root=/mnt/knowledge/workspaces
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "code-insight.storage")
public class StorageProperties {

    /** 存储模式：local（单机） / shared（集群 NAS） */
    private StorageMode mode = StorageMode.LOCAL;

    /** 草稿正文、pipeline.log 等持久化根目录（mode=local 时，log + drafts 共用此根） */
    private String localPath = "./storage";

    /** NAS 根路径（仅 mode=shared）：NAS 挂载点根目录 */
    private String basePath = "/mnt/knowledge";

    /** 草稿相对路径（仅 mode=shared）：{basePath}/{draftsRoot}/{sysId}/{repoId}/{taskId}/ */
    private String draftsRoot = "drafts";

    /** 发布产物相对路径（仅 mode=shared）：{basePath}/{releasesRoot}/{sysId}/{repoId}/{versionNum}/ */
    private String releasesRoot = "releases";

    /** Git clone 与扫描工作区根目录 */
    private String workspaceRoot = "./temp_repos";

    // ===== 路径计算 =====

    /** 任务临时工作区 */
    public String taskWorkspaceDir(long taskId) {
        return workspaceRoot + "/task_" + taskId;
    }

    /** 草稿目录：mode=shared 时按 {sysId}/{repoId}/task_{taskId} 分层 */
    public Path draftDir(Long systemId, Long repositoryId, Long taskId) {
        if (mode == StorageMode.SHARED) {
            return Paths.get(basePath, draftsRoot,
                    String.valueOf(systemId), String.valueOf(repositoryId),
                    "task_" + taskId);
        }
        // local mode：沿用历史目录 ./storage/drafts
        return Paths.get(localPath, "drafts");
    }

    /** 单条草稿文件路径 */
    public Path draftFilePath(Long systemId, Long repositoryId, Long taskId, String fileName) {
        return draftDir(systemId, repositoryId, taskId).resolve(fileName);
    }

    /** 发布产物根目录 */
    public Path releaseDir(Long systemId, Long repositoryId, String versionNum) {
        if (mode == StorageMode.SHARED) {
            return Paths.get(basePath, releasesRoot,
                    String.valueOf(systemId), String.valueOf(repositoryId), versionNum);
        }
        return Paths.get(localPath, "releases",
                String.valueOf(systemId), String.valueOf(repositoryId), versionNum);
    }

    /** pipeline.log 目录：{localPath or basePath}/logs */
    public Path logDir(Long taskId) {
        String root = mode == StorageMode.SHARED ? basePath : localPath;
        return Paths.get(root, "logs", "task_" + taskId);
    }
}
