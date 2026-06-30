package com.company.codeinsight.modules.push.strategy;

import java.io.File;
import java.time.LocalDateTime;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.knowledge.entity.KnowledgeVersion;
import com.company.codeinsight.modules.knowledge.mapper.KnowledgeVersionMapper;
import com.company.codeinsight.modules.push.entity.PushTask;
import com.company.codeinsight.modules.push.enums.PushMethod;
import com.company.codeinsight.modules.repository.entity.CodeRepository;
import com.company.codeinsight.modules.repository.mapper.CodeRepositoryMapper;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.mapper.DecompileTaskMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Git 推送策略
 *
 * 使用 JGit 将知识文档推送到远程 Git 仓库的指定分支和文件夹。
 * 推送目标配置优先使用 ci_repository 中的 push_* 字段，为空时回退使用 source repo 配置。
 */
@Slf4j
@Component("gitPushStrategy")
public class GitPushStrategy implements PushStrategy {

    @Autowired
    private CodeRepositoryMapper repositoryMapper;

    @Autowired
    private DecompileTaskMapper taskMapper;

    @Autowired
    private KnowledgeVersionMapper versionMapper;

    @Value("${code-insight.push.git.commit-prefix:docs: add code-insight knowledge}")
    private String commitPrefix;

    @Override
    public String execute(KnowledgeVersion version, PushTask task) {
        DecompileTask decompileTask = taskMapper.selectById(version.getTaskId());
        if (decompileTask == null) {
            throw new BusinessException("未找到关联的知识构建任务");
        }

        CodeRepository repo = repositoryMapper.selectById(version.getRepositoryId());
        if (repo == null) {
            throw new BusinessException("未找到关联的代码库配置");
        }

        // 推送目标配置：优先使用 push_* 专用字段，为空则回退到 source repo 配置
        String pushUrl = StringUtils.hasText(repo.getPushGitUrl()) ? repo.getPushGitUrl() : repo.getGitUrl();
        String pushBranch = StringUtils.hasText(repo.getPushBranch()) ? repo.getPushBranch() : "docs-code-insight";
        String pushUser = StringUtils.hasText(repo.getPushUsername()) ? repo.getPushUsername() : repo.getUsername();
        String pushPass = StringUtils.hasText(repo.getPushPassword()) ? repo.getPushPassword() : repo.getPassword();
        String targetFolder = StringUtils.hasText(repo.getPushTargetFolder()) ? repo.getPushTargetFolder() : "docs/code-insight";

        log.info("开始 Git 推送: version={}, pushUrl={}, pushBranch={}, targetFolder={}",
                version.getVersionNum(), pushUrl, pushBranch, targetFolder);

        File taskRepoDir = new File("temp_repos/task_" + decompileTask.getId());
        if (!taskRepoDir.exists() || !new File(taskRepoDir, ".git").exists()) {
            throw new BusinessException(
                    "本地 Git 仓库不存在: " + taskRepoDir.getAbsolutePath() + "，请确保代码已拉取");
        }

        try (Git git = Git.open(taskRepoDir)) {
            // git add <targetFolder>
            // 将 targetFolder 中的路径分隔符转换为 Git add 可识别的 pattern
            String addPattern = targetFolder.replace("\\", "/");
            git.add().addFilepattern(addPattern).call();

            // git commit -m "docs: add code-insight knowledge version vX.Y.Z"
            String commitMessage = commitPrefix + " version " + version.getVersionNum();
            git.commit().setMessage(commitMessage).call();

            // git push
            if (StringUtils.hasText(pushUser) && StringUtils.hasText(pushPass)) {
                git.push()
                        .setCredentialsProvider(
                                new UsernamePasswordCredentialsProvider(pushUser, pushPass))
                        .call();
            } else {
                git.push().call();
            }

            String pushedCommit = git.getRepository().resolve("HEAD").getName();
            log.info("Git 推送成功: version={}, commit={}", version.getVersionNum(), pushedCommit);

            // 更新 KnowledgeVersion 的目标提交信息
            version.setTargetCommit(pushedCommit);
            version.setStatus("PUSHED");
            version.setPushedAt(LocalDateTime.now());
            versionMapper.updateById(version);

            // 记录推送目标摘要到 PushTask
            task.setTargetInfo(String.format(
                    "{\"pushUrl\":\"%s\",\"pushBranch\":\"%s\",\"targetFolder\":\"%s\",\"commit\":\"%s\"}",
                    pushUrl, pushBranch, targetFolder, pushedCommit));

            return pushedCommit;

        } catch (Exception e) {
            log.error("Git 推送失败: version={}", version.getVersionNum(), e);
            throw new BusinessException("Git 推送失败: " + e.getMessage());
        }
    }

    @Override
    public PushMethod getMethod() {
        return PushMethod.GIT;
    }
}
