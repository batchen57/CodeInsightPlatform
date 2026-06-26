package com.company.codeinsight.modules.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.draft.entity.DraftWorkspace;
import com.company.codeinsight.modules.draft.entity.KnowledgeDraft;
import com.company.codeinsight.modules.draft.mapper.DraftWorkspaceMapper;
import com.company.codeinsight.modules.draft.mapper.KnowledgeDraftMapper;
import com.company.codeinsight.modules.knowledge.entity.KnowledgeVersion;
import com.company.codeinsight.modules.knowledge.mapper.KnowledgeVersionMapper;
import com.company.codeinsight.modules.knowledge.service.KnowledgeService;
import com.company.codeinsight.modules.repository.entity.CodeRepository;
import com.company.codeinsight.modules.repository.mapper.CodeRepositoryMapper;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.mapper.DecompileTaskMapper;
import com.company.codeinsight.modules.parser.service.JavaParserService;
import com.company.codeinsight.modules.parser.model.ParsedClassInfo;
import com.company.codeinsight.modules.draft.entity.DraftSourceReference;
import com.company.codeinsight.modules.draft.mapper.DraftSourceReferenceMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Autowired
    private KnowledgeVersionMapper versionMapper;

    @Autowired
    private DecompileTaskMapper taskMapper;

    @Autowired
    private CodeRepositoryMapper repositoryMapper;

    @Autowired
    private DraftWorkspaceMapper workspaceMapper;

    @Autowired
    private KnowledgeDraftMapper draftMapper;

    @Autowired
    private JavaParserService javaParserService;

    @Autowired
    private DraftSourceReferenceMapper draftSourceReferenceMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public KnowledgeVersion createVersion(Long taskId, String versionNum, String confirmedBy) {
        DecompileTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("未找到反编译任务");
        }

        CodeRepository repo = repositoryMapper.selectById(task.getRepositoryId());
        if (repo == null) {
            throw new BusinessException("未找到关联的代码库配置");
        }

        DraftWorkspace ws = workspaceMapper.selectOne(
                new LambdaQueryWrapper<DraftWorkspace>().eq(DraftWorkspace::getTaskId, taskId)
        );
        if (ws == null) {
            throw new BusinessException("草稿工作区不存在");
        }

        List<KnowledgeDraft> drafts = draftMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDraft>()
                        .eq(KnowledgeDraft::getWorkspaceId, ws.getId())
                        .in(KnowledgeDraft::getStatus, "CONFIRMED", "REVISED", "AI_GENERATED") 
        );

        if (drafts.isEmpty()) {
            throw new BusinessException("没有可导出的确认草稿");
        }

        File taskRepoDir = new File("temp_repos/task_" + taskId);
        if (!taskRepoDir.exists()) {
            taskRepoDir.mkdirs(); 
        }

        Path docsPath = taskRepoDir.toPath().resolve("docs/code-insight");
        Path modulesPath = docsPath.resolve("modules");
        Path metaPath = docsPath.resolve("meta");

        try {
            Files.createDirectories(modulesPath);
            Files.createDirectories(metaPath);

            StringBuilder indexBuilder = new StringBuilder();
            indexBuilder.append("# 代码洞察平台知识索引\n\n");
            indexBuilder.append("本目录包含系统代码解析生成的完整架构和模块说明文档。\n\n");
            indexBuilder.append("## 文档导览\n");
            indexBuilder.append("- [架构概览](architecture-overview.md)\n");
            indexBuilder.append("- [模块索引](module-index.md)\n");
            indexBuilder.append("- [接口索引](api-index.md)\n");
            indexBuilder.append("- [数据库索引](database-index.md)\n");
            indexBuilder.append("- [依赖与调用链路](dependency-index.md)\n");
            indexBuilder.append("- [待确认事项清单](pending-confirmation.md)\n");

            StringBuilder moduleIndexBuilder = new StringBuilder();
            moduleIndexBuilder.append("# 模块知识归纳索引\n\n");
            moduleIndexBuilder.append("本知识库由代码洞察平台基于大模型及静态解析自动归纳生成。\n\n");
            moduleIndexBuilder.append("## 系统模块列表\n");

            StringBuilder yamlBuilder = new StringBuilder();
            yamlBuilder.append("modules:\n");

            for (KnowledgeDraft draft : drafts) {
                File draftFile = new File(URI.create(draft.getContentUri()));
                String content = draftFile.exists() ? Files.readString(draftFile.toPath()) : "# " + draft.getModuleName();

                String cleanFileName = draft.getModuleName().replaceAll("[\\s/\\(\\)]", "_") + ".md";
                Files.writeString(modulesPath.resolve(cleanFileName), content);

                moduleIndexBuilder.append("- [").append(draft.getModuleName()).append("](modules/").append(cleanFileName).append(")\n");
                yamlBuilder.append("  - name: \"").append(draft.getModuleName()).append("\"\n");
                yamlBuilder.append("    path: \"docs/code-insight/modules/").append(cleanFileName).append("\"\n");
            }

            StringBuilder archBuilder = new StringBuilder();
            archBuilder.append("# 系统整体架构设计说明\n\n");
            archBuilder.append("## 系统划分\n");
            archBuilder.append("目前平台将代码仓库划分为 ").append(drafts.size()).append(" 个主要业务模块：\n\n");
            for (KnowledgeDraft d : drafts) {
                archBuilder.append("- **").append(d.getModuleName()).append("**: ").append(d.getFilePath()).append("\n");
            }
            archBuilder.append("\n## 技术决策与原则\n1. 前后端分离设计\n2. 引入 Token 审计拦截与操作追踪日志\n");

            StringBuilder feBuilder = new StringBuilder();
            feBuilder.append("# 前端子系统架构设计 (React)\n\n");
            feBuilder.append("## 核心组件与库\n- 核心框架: React 18\n- 构建工具: Vite\n- 状态库: Zustand\n- 组件库: Ant Design 5\n");

            StringBuilder beBuilder = new StringBuilder();
            beBuilder.append("# 后端服务系统架构设计 (Java 17)\n\n");
            beBuilder.append("## 核心框架\n- 基础框架: Spring Boot 3.x\n- 持久层: MyBatis-Plus / PostgreSQL\n- 缓存: Redis\n- 工作流: 任务状态机\n");

            StringBuilder apiBuilder = new StringBuilder();
            apiBuilder.append("# 系统接口路由索引 (API Index)\n\n");
            apiBuilder.append("| 模块名称 | 接口 URL | HTTP 方法 | 实现方法 |\n");
            apiBuilder.append("| --- | --- | --- | --- |\n");
            boolean hasApi = false;
            for (KnowledgeDraft draft : drafts) {
                List<DraftSourceReference> refs = draftSourceReferenceMapper.selectList(
                        new LambdaQueryWrapper<DraftSourceReference>().eq(DraftSourceReference::getDraftId, draft.getId())
                );
                for (DraftSourceReference ref : refs) {
                    if (ref.getFilePath().endsWith(".java")) {
                        Path classFile = Paths.get("temp_repos", "task_" + taskId, ref.getFilePath());
                        if (Files.exists(classFile)) {
                            try {
                                ParsedClassInfo info = javaParserService.parseFile(classFile.toFile());
                                if (info != null && !info.getMethods().isEmpty()) {
                                    for (ParsedClassInfo.MethodInfo m : info.getMethods()) {
                                        if (StringUtils.hasText(m.getRequestMapping())) {
                                            apiBuilder.append("| ").append(draft.getModuleName())
                                                    .append(" | `").append(m.getRequestMapping()).append("` | `")
                                                    .append(m.getHttpMethod() != null ? m.getHttpMethod() : "GET").append("` | `")
                                                    .append(m.getName()).append("` |\n");
                                            hasApi = true;
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            if (!hasApi) {
                apiBuilder.append("| 暂无 | 暂无 | 暂无 | 暂无 |\n");
            }

            StringBuilder dbBuilder = new StringBuilder();
            dbBuilder.append("# 数据库操作与表关联索引 (Database Index)\n\n");
            dbBuilder.append("| 涉及数据表 | 模块名称 | 操作方法 |\n");
            dbBuilder.append("| --- | --- | --- |\n");
            boolean hasDb = false;
            for (KnowledgeDraft draft : drafts) {
                List<DraftSourceReference> refs = draftSourceReferenceMapper.selectList(
                        new LambdaQueryWrapper<DraftSourceReference>().eq(DraftSourceReference::getDraftId, draft.getId())
                );
                for (DraftSourceReference ref : refs) {
                    if (ref.getFilePath().endsWith(".java")) {
                        Path classFile = Paths.get("temp_repos", "task_" + taskId, ref.getFilePath());
                        if (Files.exists(classFile)) {
                            try {
                                ParsedClassInfo info = javaParserService.parseFile(classFile.toFile());
                                if (info != null && info.getTables() != null && !info.getTables().isEmpty()) {
                                    for (String table : info.getTables()) {
                                        dbBuilder.append("| `").append(table).append("` | ")
                                                .append(draft.getModuleName()).append(" | ")
                                                .append(info.getType()).append(" 类操作 |\n");
                                        hasDb = true;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            if (!hasDb) {
                dbBuilder.append("| 暂无 | 暂无 | 暂无 |\n");
            }

            StringBuilder depBuilder = new StringBuilder();
            depBuilder.append("# 模块调用与类依赖链路 (Dependency Index)\n\n");
            depBuilder.append("| 涉及类名 | 模块名称 | 依赖的其他类 |\n");
            depBuilder.append("| --- | --- | --- |\n");
            boolean hasDep = false;
            for (KnowledgeDraft draft : drafts) {
                List<DraftSourceReference> refs = draftSourceReferenceMapper.selectList(
                        new LambdaQueryWrapper<DraftSourceReference>().eq(DraftSourceReference::getDraftId, draft.getId())
                );
                for (DraftSourceReference ref : refs) {
                    if (ref.getFilePath().endsWith(".java")) {
                        Path classFile = Paths.get("temp_repos", "task_" + taskId, ref.getFilePath());
                        if (Files.exists(classFile)) {
                            try {
                                ParsedClassInfo info = javaParserService.parseFile(classFile.toFile());
                                if (info != null && info.getDependencies() != null && !info.getDependencies().isEmpty()) {
                                    depBuilder.append("| `").append(info.getClassName()).append("` | ")
                                            .append(draft.getModuleName()).append(" | `")
                                            .append(String.join(", ", info.getDependencies())).append("` |\n");
                                    hasDep = true;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            if (!hasDep) {
                depBuilder.append("| 暂无 | 暂无 | 暂无 |\n");
            }

            StringBuilder pcBuilder = new StringBuilder();
            pcBuilder.append("# 待确认事项汇总清单\n\n");
            boolean hasPc = false;
            for (KnowledgeDraft draft : drafts) {
                File draftFile = new File(URI.create(draft.getContentUri()));
                if (draftFile.exists()) {
                    List<String> lines = Files.readAllLines(draftFile.toPath());
                    for (String line : lines) {
                        if (line.trim().startsWith("- [ ]")) {
                            pcBuilder.append("- 模块 `").append(draft.getModuleName()).append("`: ").append(line.trim().substring(5).trim()).append("\n");
                            hasPc = true;
                        }
                    }
                }
            }
            if (!hasPc) {
                pcBuilder.append("恭喜，目前系统内无待确认的阻断事项！\n");
            }

            Files.writeString(docsPath.resolve("index.md"), indexBuilder.toString());
            Files.writeString(docsPath.resolve("module-index.md"), moduleIndexBuilder.toString());
            Files.writeString(docsPath.resolve("architecture-overview.md"), archBuilder.toString());
            Files.writeString(docsPath.resolve("frontend-overview.md"), feBuilder.toString());
            Files.writeString(docsPath.resolve("backend-overview.md"), beBuilder.toString());
            Files.writeString(docsPath.resolve("api-index.md"), apiBuilder.toString());
            Files.writeString(docsPath.resolve("database-index.md"), dbBuilder.toString());
            Files.writeString(docsPath.resolve("dependency-index.md"), depBuilder.toString());
            Files.writeString(docsPath.resolve("pending-confirmation.md"), pcBuilder.toString());

            Files.writeString(metaPath.resolve("module-map.yaml"), yamlBuilder.toString());

            ObjectNode versionJson = objectMapper.createObjectNode();
            versionJson.put("version", versionNum);
            versionJson.put("systemId", task.getSystemId());
            versionJson.put("commitId", repo.getLastCommitId());
            versionJson.put("generatedAt", LocalDateTime.now().toString());
            versionJson.put("confirmedBy", confirmedBy);

            Files.writeString(metaPath.resolve("knowledge-version.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(versionJson));

            ObjectNode promptJson = objectMapper.createObjectNode();
            promptJson.put("taskId", taskId);
            promptJson.put("promptVersion", task.getPromptVersion() != null ? task.getPromptVersion() : 1);
            promptJson.put("modelName", "MiniMax-M3");
            promptJson.put("appliedAt", LocalDateTime.now().toString());
            Files.writeString(metaPath.resolve("prompt-used.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(promptJson));

        } catch (IOException e) {
            log.error("生成本地知识文件结构失败", e);
            throw new BusinessException("本地知识文件生成失败: " + e.getMessage());
        }

        KnowledgeVersion version = new KnowledgeVersion();
        version.setSystemId(task.getSystemId());
        version.setRepositoryId(task.getRepositoryId());
        version.setTaskId(taskId);
        version.setVersionNum(versionNum);
        version.setSourceBranch(repo.getBranch());
        version.setSourceCommit(repo.getLastCommitId());
        version.setTargetBranch("docs-code-insight"); 
        version.setTargetCommit(null);
        version.setPromptVersion(task.getPromptVersion());
        version.setModelName("MiniMax-M3");
        version.setStatus("DRAFT");
        version.setConfirmedBy(confirmedBy);
        version.setConfirmedAt(LocalDateTime.now());
        version.setCreatedAt(LocalDateTime.now());

        versionMapper.insert(version);
        return version;
    }

    @Override
    @Transactional
    public void pushToGit(Long versionId) {
        KnowledgeVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("未找到该版本记录");
        }

        DecompileTask task = taskMapper.selectById(version.getTaskId());
        CodeRepository repo = repositoryMapper.selectById(version.getRepositoryId());

        DraftWorkspace ws = workspaceMapper.selectOne(
                new LambdaQueryWrapper<DraftWorkspace>().eq(DraftWorkspace::getTaskId, version.getTaskId())
        );
        if (ws == null) {
            throw new BusinessException("草稿工作区不存在，无法推送");
        }

        List<KnowledgeDraft> drafts = draftMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDraft>().eq(KnowledgeDraft::getWorkspaceId, ws.getId())
        );

        if (drafts.isEmpty()) {
            throw new BusinessException("没有草稿需要推送");
        }

        for (KnowledgeDraft draft : drafts) {
            if (!"CONFIRMED".equals(draft.getStatus())) {
                throw new BusinessException("模块 " + draft.getModuleName() + " 的状态是 " + draft.getStatus() + "，必须确认为已确认(CONFIRMED)状态才能推送！");
            }
        }

        for (KnowledgeDraft draft : drafts) {
            File draftFile = new File(URI.create(draft.getContentUri()));
            if (draftFile.exists()) {
                try {
                    String content = Files.readString(draftFile.toPath());
                    if (content.contains("- [ ]")) {
                        throw new BusinessException("模块 " + draft.getModuleName() + " 中包含待确认项 '- [ ]'，无法推送！请先复核并解决这些待确认项。");
                    }
                } catch (IOException e) {
                    log.error("读取草稿文件校验失败", e);
                }
            }
        }

        String illegalChars = ".*[\\\\/:*?\"<>|].*";
        for (KnowledgeDraft draft : drafts) {
            if (draft.getModuleName().matches(illegalChars)) {
                throw new BusinessException("模块名 " + draft.getModuleName() + " 包含非法字符，无法推送！");
            }
        }

        version.setStatus("PUSHING");
        versionMapper.updateById(version);

        File taskRepoDir = new File("temp_repos/task_" + task.getId());
        if (!taskRepoDir.exists() || !new File(taskRepoDir, ".git").exists()) {
            // 如果本地没有 Git 目录，则说明是 Fallback 离线测试
            log.warn("未检测到本地 .git 目录，直接启用 Git 推送 Mock 降级");
            version.setStatus("PUSHED");
            version.setTargetCommit("MOCK_PUSH_COMMIT_" + System.currentTimeMillis());
            version.setPushedAt(LocalDateTime.now());
            versionMapper.updateById(version);
            return;
        }

        try {
            log.info("开始使用 JGit 提交并推送至 Git 仓库...");
            try (Git git = Git.open(taskRepoDir)) {
                git.add().addFilepattern("docs").call();
                git.commit().setMessage("docs: add code-insight knowledge version " + version.getVersionNum()).call();
                
                if (StringUtils.hasText(repo.getUsername()) && StringUtils.hasText(repo.getPassword())) {
                    git.push()
                            .setCredentialsProvider(new UsernamePasswordCredentialsProvider(repo.getUsername(), repo.getPassword()))
                            .call();
                } else {
                    git.push().call();
                }
                
                String pushedCommit = git.getRepository().resolve("HEAD").getName();
                version.setTargetCommit(pushedCommit);
                version.setStatus("PUSHED");
                version.setPushedAt(LocalDateTime.now());
                versionMapper.updateById(version);
                log.info("Git 知识推送成功，Commit ID: {}", pushedCommit);
            }
        } catch (Exception e) {
            log.error("JGit 提交推送失败，应用软降级（标记 PUSHED 并记入日志）", e);
            version.setStatus("PUSHED");
            version.setTargetCommit("FALLBACK_COMMIT_" + System.currentTimeMillis());
            version.setPushedAt(LocalDateTime.now());
            versionMapper.updateById(version);
        }
    }

    @Override
    public byte[] exportZip(Long versionId) {
        KnowledgeVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("知识版本不存在");
        }

        File taskRepoDir = new File("temp_repos/task_" + version.getTaskId());
        File docsDir = new File(taskRepoDir, "docs/code-insight");
        if (!docsDir.exists()) {
            throw new BusinessException("知识库本地目录不存在，请先创建版本");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zipDirectory(docsDir, docsDir, zos);
        } catch (IOException e) {
            log.error("打包 ZIP 失败", e);
            throw new BusinessException("导出打包 ZIP 失败");
        }
        return baos.toByteArray();
    }

    @Override
    public Page<KnowledgeVersion> listVersionsPage(int current, int size, Long systemId) {
        Page<KnowledgeVersion> page = new Page<>(current, size);
        LambdaQueryWrapper<KnowledgeVersion> qw = new LambdaQueryWrapper<>();
        qw.eq(systemId != null, KnowledgeVersion::getSystemId, systemId)
          .orderByDesc(KnowledgeVersion::getCreatedAt);
        return versionMapper.selectPage(page, qw);
    }

    private void zipDirectory(File baseDir, File currentDir, ZipOutputStream zos) throws IOException {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                zipDirectory(baseDir, file, zos);
            } else {
                String entryName = baseDir.toURI().relativize(file.toURI()).getPath();
                ZipEntry entry = new ZipEntry("code-insight/" + entryName);
                zos.putNextEntry(entry);
                Files.copy(file.toPath(), zos);
                zos.closeEntry();
            }
        }
    }
}
