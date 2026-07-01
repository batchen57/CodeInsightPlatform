package com.company.codeinsight.modules.knowledge.browse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.draft.entity.DraftWorkspace;
import com.company.codeinsight.modules.draft.entity.KnowledgeDraft;
import com.company.codeinsight.modules.draft.mapper.DraftWorkspaceMapper;
import com.company.codeinsight.modules.draft.mapper.KnowledgeDraftMapper;
import com.company.codeinsight.modules.hierarchy.model.FunctionDto;
import com.company.codeinsight.modules.hierarchy.model.ModuleDto;
import com.company.codeinsight.modules.hierarchy.model.ModuleHierarchy;
import com.company.codeinsight.modules.hierarchy.model.SubModuleDto;
import com.company.codeinsight.modules.hierarchy.service.ModuleHierarchyService;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseTreeNode;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseTreeQuery;
import com.company.codeinsight.modules.knowledge.browse.dto.KnowledgeBrowseTreeResult;
import com.company.codeinsight.modules.repository.entity.CodeRepository;
import com.company.codeinsight.modules.repository.mapper.CodeRepositoryMapper;
import com.company.codeinsight.modules.system.entity.SystemApplication;
import com.company.codeinsight.modules.system.mapper.SystemApplicationMapper;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.mapper.DecompileTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 知识查看树形模式：按模块层级展示功能叶子，并关联知识草稿。
 */
@Slf4j
@Service
public class KnowledgeBrowseTreeService {

    private static final List<String> DOC_READY_STATUSES = List.of(
            "PENDING_REVIEW", "REVIEWING", "CONFIRMED", "PUSHED", "PUSHING"
    );

    @Autowired
    private DecompileTaskMapper taskMapper;

    @Autowired
    private ModuleHierarchyService moduleHierarchyService;

    @Autowired
    private DraftWorkspaceMapper workspaceMapper;

    @Autowired
    private KnowledgeDraftMapper draftMapper;

    @Autowired
    private SystemApplicationMapper systemMapper;

    @Autowired
    private CodeRepositoryMapper repositoryMapper;

    @Value("${code-insight.doc-generation.granularity:function}")
    private String docGenerationGranularity;

    public KnowledgeBrowseTreeResult buildTree(KnowledgeBrowseTreeQuery query) {
        if (query == null || query.getSystemId() == null || query.getRepositoryId() == null) {
            throw new BusinessException("树形模式需选择系统与仓库");
        }

        Long systemId = query.getSystemId();
        Long repositoryId = query.getRepositoryId();
        boolean autoResolved = query.getTaskId() == null;
        DecompileTask task = resolveBenchmarkTask(systemId, repositoryId, query.getTaskId());

        KnowledgeBrowseTreeResult result = new KnowledgeBrowseTreeResult();
        result.setSystemId(systemId);
        result.setRepositoryId(repositoryId);
        result.setTaskId(task.getId());
        result.setTaskAutoResolved(autoResolved);
        result.setDocumentGranularity(docGenerationGranularity.toLowerCase(Locale.ROOT));

        SystemApplication system = systemMapper.selectById(systemId);
        result.setSystemName(formatSystemName(system));

        CodeRepository repo = repositoryMapper.selectById(repositoryId);
        result.setRepositoryName(formatRepositoryName(repo));

        ModuleHierarchy hierarchy = moduleHierarchyService.loadByTaskId(task.getId());
        if (hierarchy.getModules() == null || hierarchy.getModules().isEmpty()) {
            result.setNodes(new ArrayList<>());
            return result;
        }

        Map<String, KnowledgeDraft> draftByPath = loadDraftPathIndex(task.getId());
        Map<String, KnowledgeDraft> draftByModuleName = loadDraftModuleNameIndex(task.getId());
        boolean functionGranularity = "function".equalsIgnoreCase(docGenerationGranularity);

        List<KnowledgeBrowseTreeNode> roots = new ArrayList<>();
        for (ModuleDto module : hierarchy.getModules().values()) {
            KnowledgeBrowseTreeNode moduleNode = new KnowledgeBrowseTreeNode();
            moduleNode.setKey("module:" + module.getId());
            moduleNode.setNodeType("MODULE");
            moduleNode.setTitle(module.getModuleName());

            KnowledgeDraft moduleDraft = null;
            if (!functionGranularity) {
                moduleDraft = resolveModuleDraft(task.getId(), module, draftByPath, draftByModuleName);
            }

            List<KnowledgeBrowseTreeNode> subNodes = new ArrayList<>();
            for (SubModuleDto subModule : module.getSubModules().values()) {
                KnowledgeBrowseTreeNode subNode = new KnowledgeBrowseTreeNode();
                subNode.setKey("sub:" + subModule.getId());
                subNode.setNodeType("SUB_MODULE");
                subNode.setTitle(subModule.getSubModuleName());

                List<KnowledgeBrowseTreeNode> fnNodes = new ArrayList<>();
                for (FunctionDto fn : subModule.getFunctions().values()) {
                    fnNodes.add(buildFunctionNode(task.getId(), module, subModule, fn,
                            functionGranularity, moduleDraft, draftByPath, draftByModuleName));
                }
                subNode.setChildren(fnNodes);
                subNodes.add(subNode);
            }
            moduleNode.setChildren(subNodes);
            roots.add(moduleNode);
        }
        result.setNodes(roots);
        return result;
    }

    private KnowledgeBrowseTreeNode buildFunctionNode(Long taskId, ModuleDto module, SubModuleDto subModule,
                                                      FunctionDto fn, boolean functionGranularity,
                                                      KnowledgeDraft moduleDraft,
                                                      Map<String, KnowledgeDraft> draftByPath,
                                                      Map<String, KnowledgeDraft> draftByModuleName) {
        KnowledgeBrowseTreeNode fnNode = new KnowledgeBrowseTreeNode();
        fnNode.setKey("fn:" + fn.getId());
        fnNode.setNodeType("FUNCTION");
        fnNode.setTitle(fn.getFunctionName());
        fnNode.setChildren(new ArrayList<>());

        KnowledgeDraft draft;
        if (functionGranularity) {
            fnNode.setDocumentGranularity("function");
            draft = resolveFunctionDraft(taskId, module, subModule, fn, draftByPath, draftByModuleName);
        } else {
            fnNode.setDocumentGranularity("module");
            draft = moduleDraft;
        }

        if (draft != null) {
            fnNode.setHasDocument(true);
            fnNode.setDraftId(draft.getId());
            fnNode.setDraftStatus(draft.getStatus());
        } else {
            fnNode.setHasDocument(false);
        }
        return fnNode;
    }

    private KnowledgeDraft resolveFunctionDraft(Long taskId, ModuleDto module, SubModuleDto subModule,
                                                FunctionDto fn,
                                                Map<String, KnowledgeDraft> draftByPath,
                                                Map<String, KnowledgeDraft> draftByModuleName) {
        String path = buildFunctionDocPath(taskId, module.getModuleName(), subModule.getSubModuleName(), fn.getFunctionName());
        KnowledgeDraft draft = draftByPath.get(path);
        if (draft != null) {
            return draft;
        }
        String label = module.getModuleName() + " / " + subModule.getSubModuleName() + " / " + fn.getFunctionName();
        draft = draftByModuleName.get(label);
        if (draft != null) {
            return draft;
        }
        return draftByModuleName.get(normalize(label));
    }

    private KnowledgeDraft resolveModuleDraft(Long taskId, ModuleDto module,
                                              Map<String, KnowledgeDraft> draftByPath,
                                              Map<String, KnowledgeDraft> draftByModuleName) {
        String path = buildModuleDocPath(taskId, module.getModuleName());
        KnowledgeDraft draft = draftByPath.get(path);
        if (draft != null) {
            return draft;
        }
        draft = draftByModuleName.get(module.getModuleName());
        if (draft != null) {
            return draft;
        }
        return draftByModuleName.get(normalize(module.getModuleName()));
    }

    private Map<String, KnowledgeDraft> loadDraftPathIndex(Long taskId) {
        DraftWorkspace ws = workspaceMapper.selectOne(
                new LambdaQueryWrapper<DraftWorkspace>().eq(DraftWorkspace::getTaskId, taskId));
        if (ws == null) {
            return Map.of();
        }
        List<KnowledgeDraft> drafts = draftMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDraft>().eq(KnowledgeDraft::getWorkspaceId, ws.getId()));
        Map<String, KnowledgeDraft> out = new HashMap<>();
        for (KnowledgeDraft d : drafts) {
            if (StringUtils.hasText(d.getFilePath())) {
                out.put(d.getFilePath(), d);
            }
        }
        return out;
    }

    private Map<String, KnowledgeDraft> loadDraftModuleNameIndex(Long taskId) {
        DraftWorkspace ws = workspaceMapper.selectOne(
                new LambdaQueryWrapper<DraftWorkspace>().eq(DraftWorkspace::getTaskId, taskId));
        if (ws == null) {
            return Map.of();
        }
        List<KnowledgeDraft> drafts = draftMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDraft>().eq(KnowledgeDraft::getWorkspaceId, ws.getId()));
        Map<String, KnowledgeDraft> out = new HashMap<>();
        for (KnowledgeDraft d : drafts) {
            if (StringUtils.hasText(d.getModuleName())) {
                out.put(d.getModuleName(), d);
                out.put(normalize(d.getModuleName()), d);
            }
        }
        return out;
    }

    private DecompileTask resolveBenchmarkTask(Long systemId, Long repositoryId, Long taskIdOverride) {
        if (taskIdOverride != null) {
            DecompileTask task = taskMapper.selectById(taskIdOverride);
            if (task == null) {
                throw new BusinessException("任务不存在: " + taskIdOverride);
            }
            if (!systemId.equals(task.getSystemId()) || !repositoryId.equals(task.getRepositoryId())) {
                throw new BusinessException("所选任务与系统/仓库不匹配");
            }
            return task;
        }
        DecompileTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<DecompileTask>()
                        .eq(DecompileTask::getSystemId, systemId)
                        .eq(DecompileTask::getRepositoryId, repositoryId)
                        .in(DecompileTask::getStatus, DOC_READY_STATUSES)
                        .orderByDesc(DecompileTask::getId)
                        .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException("该仓库下尚无已生成文档的任务，请先完成知识构建");
        }
        return task;
    }

    static String buildFunctionDocPath(Long taskId, String moduleName, String subModuleName, String functionName) {
        return "task_" + taskId + "/"
                + safeSegment(moduleName) + "/"
                + safeSegment(subModuleName) + "/"
                + safeSegment(functionName) + ".md";
    }

    static String buildModuleDocPath(Long taskId, String moduleName) {
        return "task_" + taskId + "/" + safeSegment(moduleName) + ".md";
    }

    static String safeSegment(String raw) {
        if (raw == null) {
            return "_";
        }
        return raw.replaceAll("[\\s/\\(\\)]", "_");
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    static String formatSystemName(SystemApplication system) {
        if (system == null) {
            return null;
        }
        if (StringUtils.hasText(system.getNameCn())) {
            return system.getNameCn();
        }
        return system.getName();
    }

    static String formatRepositoryName(CodeRepository repo) {
        if (repo == null) {
            return null;
        }
        String url = repo.getGitUrl();
        String base = "仓库 #" + repo.getId();
        if (StringUtils.hasText(url)) {
            int idx = Math.max(url.lastIndexOf('/'), url.lastIndexOf('\\'));
            base = idx >= 0 ? url.substring(idx + 1) : url;
            if (base.endsWith(".git")) {
                base = base.substring(0, base.length() - 4);
            }
        }
        if (StringUtils.hasText(repo.getBranch())) {
            return base + " (" + repo.getBranch() + ")";
        }
        return base;
    }
}
