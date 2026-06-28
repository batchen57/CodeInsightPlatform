package com.company.codeinsight.modules.hierarchy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.common.util.Base62Generator;
import com.company.codeinsight.common.util.PromptTemplateLoader;
import com.company.codeinsight.modules.ai.service.AiSummaryService;
import com.company.codeinsight.modules.entrypoint.model.EntryPoint;
import com.company.codeinsight.modules.entrypoint.model.EntryPointConfig;
import com.company.codeinsight.modules.entrypoint.model.EntryPointConfigCodec;
import com.company.codeinsight.modules.entrypoint.service.EntryPointDiscoveryService;
import com.company.codeinsight.modules.hierarchy.entity.ModuleHierarchyNode;
import com.company.codeinsight.modules.hierarchy.mapper.ModuleHierarchyNodeMapper;
import com.company.codeinsight.modules.hierarchy.model.FunctionDto;
import com.company.codeinsight.modules.hierarchy.model.ModuleDto;
import com.company.codeinsight.modules.hierarchy.model.ModuleHierarchy;
import com.company.codeinsight.modules.hierarchy.model.PromptViewDtos;
import com.company.codeinsight.modules.hierarchy.model.SubModuleDto;
import com.company.codeinsight.modules.hierarchy.service.ModuleHierarchyService;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.mapper.DecompileTaskMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模块层级服务实现
 * - 加载已有节点 → DTO 重建
 * - 入口逐个调 AI → 解析 JSON → 复用/新增 ID → 注入 classPaths
 * - 全量重写表（delete + batch insert）保证幂等
 */
@Slf4j
@Service
public class ModuleHierarchyServiceImpl implements ModuleHierarchyService {

    /**
     * 模块提取提示词的 classpath 兜底文件路径
     * <p>仅当 DB 中所有 MODULARIZE 提示词都不可用（如未执行 seed）时使用，保证旧任务也能跑通。</p>
     */
    private static final String MODULARIZE_PROMPT_FALLBACK_PATH = "analyze_prompt.md";

    private static final String LEVEL_MODULE = "MODULE";
    private static final String LEVEL_SUB_MODULE = "SUB_MODULE";
    private static final String LEVEL_FUNCTION = "FUNCTION";

    @Autowired
    private ModuleHierarchyNodeMapper nodeMapper;

    @Autowired
    private DecompileTaskMapper taskMapper;

    @Autowired
    private com.company.codeinsight.modules.repository.mapper.CodeRepositoryMapper repositoryMapper;

    @Autowired
    private EntryPointDiscoveryService entryPointDiscoveryService;

    @Autowired
    @Lazy
    private AiSummaryService aiSummaryService;

    @Autowired
    private PromptTemplateLoader promptTemplateLoader;

    @Autowired
    private Base62Generator base62Generator;

    @Autowired
    private com.company.codeinsight.modules.prompt.service.DecompilePromptService decompilePromptService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModuleHierarchy buildAndPersist(Long taskId, File projectDir) {
        if (taskId == null) {
            throw new BusinessException("taskId 不能为空");
        }
        DecompileTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }

        // 1. 加载已有节点 → 重建 DTO
        ModuleHierarchy hierarchy = loadByTaskId(taskId);
        hierarchy.setTaskId(taskId);
        hierarchy.setSystemId(task.getSystemId());

        // 1.5 从 task 配置还原 EntryPointConfig（任务级 null 时回退到仓库级，再 null 走默认 Controller/JOB/MQ 兜底）
        EntryPointConfig entryPointConfig = EntryPointConfigCodec.decode(task.getEntryScanConfig());
        if (entryPointConfig.isIncludeAllEmpty()
                && entryPointConfig.getEffectiveIncludeAnnotations().isEmpty()
                && entryPointConfig.getEffectiveIncludeClasspaths().isEmpty()
                && entryPointConfig.getEffectiveIncludeExtends().isEmpty()
                && entryPointConfig.getEffectiveExcludeClasspaths().isEmpty()
                && entryPointConfig.getEffectiveExcludePackages().isEmpty()
                && entryPointConfig.getEffectiveExcludeAnnotations().isEmpty()) {
            // 任务级全空 → 尝试从仓库表读默认配置
            com.company.codeinsight.modules.repository.entity.CodeRepository repo =
                    repositoryMapper.selectById(task.getRepositoryId());
            if (repo != null) {
                entryPointConfig = EntryPointConfigCodec.decode(repo.getEntryScanConfig());
                log.info("taskId={} 任务级 EntryPointConfig 为空，回退使用仓库 {} (repositoryId={}) 的默认配置",
                        taskId, repo.getGitUrl(), task.getRepositoryId());
            }
        }

        // 2. 识别入口
        List<EntryPoint> entries = entryPointDiscoveryService.discoverEntries(taskId, projectDir, entryPointConfig);
        log.info("ModuleHierarchyService.buildAndPersist taskId={} entries={}", taskId, entries.size());

        // 3. 加载 prompt 模板（仅一次）
        //    优先 DB（按 task.modularizePromptVersion 或同类型默认），找不到再回退到 classpath 资源，保证向后兼容
        String promptTemplate = decompilePromptService.resolveTaskPromptContent(task,
                com.company.codeinsight.modules.prompt.entity.DecompilePrompt.TYPE_MODULARIZE);
        if (!StringUtils.hasText(promptTemplate)) {
            log.warn("taskId={} 数据库中无 MODULARIZE 提示词可用，回退到 classpath 资源 {}", taskId, MODULARIZE_PROMPT_FALLBACK_PATH);
            promptTemplate = promptTemplateLoader.load(MODULARIZE_PROMPT_FALLBACK_PATH);
        }

        // 4. 遍历每个入口
        for (EntryPoint entry : entries) {
            try {
                processEntry(task, hierarchy, entry, promptTemplate, projectDir, entryPointConfig);
            } catch (Exception e) {
                log.error("processEntry failed for {}: {}", entry.getClassName(), e.getMessage(), e);
            }
        }

        // 5. 全量重写（保证幂等）
        persistAll(taskId, task.getSystemId(), hierarchy);

        log.info("ModuleHierarchyService.buildAndPersist done. taskId={} modules={} functions={}",
                taskId, hierarchy.getModules().size(), countFunctions(hierarchy));
        return hierarchy;
    }

    @Override
    public ModuleHierarchy loadByTaskId(Long taskId) {
        ModuleHierarchy hierarchy = new ModuleHierarchy();
        hierarchy.setTaskId(taskId);

        List<ModuleHierarchyNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<ModuleHierarchyNode>()
                        .eq(ModuleHierarchyNode::getTaskId, taskId)
                        .orderByAsc(ModuleHierarchyNode::getId)
        );
        if (nodes.isEmpty()) {
            return hierarchy;
        }
        hierarchy.setSystemId(nodes.get(0).getSystemId());

        // 用 id 索引，方便后续按 parent_id 关联
        Map<Long, ModuleHierarchyNode> idToNode = new HashMap<>();
        for (ModuleHierarchyNode n : nodes) {
            idToNode.put(n.getId(), n);
        }

        for (ModuleHierarchyNode n : nodes) {
            if (LEVEL_MODULE.equals(n.getLevel())) {
                ModuleDto m = new ModuleDto();
                m.setId(n.getNodeId());
                m.setModuleName(n.getName());
                m.setKeywords(parseJsonArray(n.getKeywords()));
                hierarchy.getModules().put(m.getId(), m);
            } else if (LEVEL_SUB_MODULE.equals(n.getLevel()) && n.getParentId() != null) {
                ModuleHierarchyNode parent = idToNode.get(n.getParentId());
                if (parent != null && LEVEL_MODULE.equals(parent.getLevel())) {
                    ModuleDto m = hierarchy.getModules().get(parent.getNodeId());
                    if (m == null) {
                        // 父级缺失（异常数据），跳过
                        continue;
                    }
                    SubModuleDto sm = new SubModuleDto();
                    sm.setId(n.getNodeId());
                    sm.setSubModuleName(n.getName());
                    sm.setKeywords(parseJsonArray(n.getKeywords()));
                    m.getSubModules().put(sm.getId(), sm);
                }
            } else if (LEVEL_FUNCTION.equals(n.getLevel()) && n.getParentId() != null) {
                ModuleHierarchyNode parent = idToNode.get(n.getParentId());
                if (parent != null && LEVEL_SUB_MODULE.equals(parent.getLevel())) {
                    ModuleHierarchyNode grand = idToNode.get(parent.getParentId());
                    if (grand == null || !LEVEL_MODULE.equals(grand.getLevel())) {
                        continue;
                    }
                    ModuleDto m = hierarchy.getModules().get(grand.getNodeId());
                    SubModuleDto sm = m == null ? null : m.getSubModules().get(parent.getNodeId());
                    if (m == null || sm == null) {
                        continue;
                    }
                    FunctionDto fn = new FunctionDto();
                    fn.setId(n.getNodeId());
                    fn.setFunctionName(n.getName());
                    fn.setClassPaths(new LinkedHashSet<>(parseJsonArray(n.getClassPaths())));
                    fn.setMethodSignatures(new LinkedHashSet<>(parseJsonArray(n.getMethodSignatures())));
                    sm.getFunctions().put(fn.getId(), fn);
                }
            }
        }
        return hierarchy;
    }

    // ============================ private helpers ============================

    /**
     * 处理单个入口：渲染 prompt → 调 AI → 解析 JSON → 合并到 DTO → 注入 classPaths
     */
    private void processEntry(DecompileTask task, ModuleHierarchy hierarchy,
                              EntryPoint entry, String promptTemplate, File projectDir,
                              EntryPointConfig entryPointConfig) {
        // 只读取入口类自身源码（不做 BFS 依赖展开），AI 看入口类方法签名和注解即可判定业务领域归属
        String javaCode = entryPointDiscoveryService.readEntrySource(projectDir, entry, entryPointConfig);
        if (!StringUtils.hasText(javaCode)) {
            log.warn("入口 {} 无可读源文件或命中排除规则，跳过", entry.getClassName());
            return;
        }

        // 业务知识库 + 已有层级（这里用空字符串，DTO 本身就是合并的权威源）
        String businessKnowledge = readOptionalFile(
                Paths.get("temp_repos", "task_" + task.getId(), "docs", "code-insight", "meta", "business_knowledge.md")
        );
        String existingHierarchyJson = serializeHierarchyToJson(hierarchy);

        String promptInput = promptTemplateLoader.render(promptTemplate, javaCode, businessKnowledge, existingHierarchyJson);
        if (promptTemplateLoader.hasUnresolvedPlaceholders(promptInput)) {
            log.warn("Prompt 仍有未替换占位符，跳过入口 {}", entry.getClassName());
            return;
        }

        AiSummaryService.AiCallMeta meta = new AiSummaryService.AiCallMeta();
        meta.setCallStage("MODULE_HIERARCHY");
        meta.setClassPath(entry.getClassName());

        // 带重试的 AI 调用 + JSON 解析：最多尝试 2 次，解析失败时附加错误信息重试
        int maxAttempts = 2;
        String currentPrompt = promptInput;
        JsonNode inc = null;
        boolean parsed = false;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String aiResponse = aiSummaryService.summarizeWithPrompt(task.getId(), currentPrompt, task.getModelName(), meta);
            if (!StringUtils.hasText(aiResponse) || "{}".equals(aiResponse.trim())) {
                if (attempt < maxAttempts) {
                    log.info("入口 {} AI 返回为空（第 {} 次），重试", entry.getClassName(), attempt);
                    continue;
                }
                log.warn("入口 {} AI 经过 {} 次尝试仍未返回有效响应，跳过", entry.getClassName(), maxAttempts);
                return;
            }

            try {
                String cleaned = stripCodeFence(aiResponse);
                inc = objectMapper.readTree(cleaned);
                parsed = true;
                break;
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                log.warn("入口 {} AI 响应 JSON 解析失败（第 {} 次）: {}", entry.getClassName(), attempt, errorMsg);
                if (attempt < maxAttempts) {
                    // 附加错误信息重试，引导 AI 修正输出
                    currentPrompt = promptInput + "\n\n[系统提示] 上轮输出 JSON 解析失败：" + errorMsg
                            + "\n请确保输出是合法的 JSON 格式（用 ```json ... ``` 包裹），字段约束见上方模板。";
                } else {
                    log.warn("入口 {} AI 响应 JSON 解析已重试 {} 次仍失败，跳过。原始响应前 200 字符: {}",
                            entry.getClassName(), maxAttempts,
                            aiResponse.substring(0, Math.min(200, aiResponse.length())));
                    return;
                }
            }
        }

        if (!parsed || inc == null) return;

        // 合并到 DTO
        Set<String> newlyCreatedFunctionIds = new LinkedHashSet<>();
        mergeIncrementIntoHierarchy(hierarchy, inc, newlyCreatedFunctionIds);

        // 把当前入口 className 注入到本次新增 function 的 classPaths
        for (String fnId : newlyCreatedFunctionIds) {
            FunctionDto fn = findFunctionById(hierarchy, fnId);
            if (fn != null) {
                fn.getClassPaths().add(entry.getClassName());
            }
        }
    }

    /**
     * 把 AI 返回的增量 JSON 合并进 DTO（复用已有 ID / 不存在则生成）
     * @param newlyCreatedFunctionIds 输出本次新增的 function id 列表
     */
    private void mergeIncrementIntoHierarchy(ModuleHierarchy hierarchy, JsonNode increment,
                                             Set<String> newlyCreatedFunctionIds) {
        JsonNode modulesNode = increment.path("modules");
        if (!modulesNode.isArray()) {
            return;
        }
        Set<String> existingModuleIds = new HashSet<>(hierarchy.getModules().keySet());
        Set<String> existingSubModuleIds = new HashSet<>();
        Set<String> existingFunctionIds = new HashSet<>();
        for (ModuleDto m : hierarchy.getModules().values()) {
            existingSubModuleIds.addAll(m.getSubModules().keySet());
            for (SubModuleDto sm : m.getSubModules().values()) {
                existingFunctionIds.addAll(sm.getFunctions().keySet());
            }
        }

        for (JsonNode modNode : modulesNode) {
            String modId = modNode.path("id").asText("");
            if (!StringUtils.hasText(modId)) continue;
            ModuleDto module = hierarchy.getModules().get(modId);
            if (module == null) {
                module = new ModuleDto();
                module.setId(modId);
                hierarchy.getModules().put(modId, module);
            }
            if (StringUtils.hasText(modNode.path("module_name").asText(""))) {
                module.setModuleName(modNode.path("module_name").asText());
            }
            mergeKeywords(module.getKeywords(), modNode.path("keywords"));

            JsonNode subsNode = modNode.path("sub_modules");
            if (!subsNode.isArray()) continue;
            for (JsonNode subNode : subsNode) {
                String subId = subNode.path("id").asText("");
                if (!StringUtils.hasText(subId)) continue;
                if (existingSubModuleIds.contains(subId) && !module.getSubModules().containsKey(subId)) {
                    log.warn("AI 输出子模块 ID {} 与同任务其他模块冲突，重新生成", subId);
                    subId = base62Generator.generateUnique('s', existingSubModuleIds);
                }
                SubModuleDto sub = module.getSubModules().get(subId);
                if (sub == null) {
                    sub = new SubModuleDto();
                    sub.setId(subId);
                    module.getSubModules().put(subId, sub);
                    existingSubModuleIds.add(subId);
                }
                if (StringUtils.hasText(subNode.path("sub_module_name").asText(""))) {
                    sub.setSubModuleName(subNode.path("sub_module_name").asText());
                }
                mergeKeywords(sub.getKeywords(), subNode.path("keywords"));

                JsonNode fnsNode = subNode.path("functions");
                if (!fnsNode.isArray()) continue;
                for (JsonNode fnNode : fnsNode) {
                    String fnId = fnNode.path("id").asText("");
                    if (!StringUtils.hasText(fnId)) continue;
                    if (existingFunctionIds.contains(fnId) && !sub.getFunctions().containsKey(fnId)) {
                        log.warn("AI 输出功能 ID {} 与同任务其他子模块冲突，重新生成", fnId);
                        fnId = base62Generator.generateUnique('f', existingFunctionIds);
                    }
                    FunctionDto fn = sub.getFunctions().get(fnId);
                    if (fn == null) {
                        fn = new FunctionDto();
                        fn.setId(fnId);
                        sub.getFunctions().put(fnId, fn);
                        existingFunctionIds.add(fnId);
                        newlyCreatedFunctionIds.add(fnId);
                    }
                    if (StringUtils.hasText(fnNode.path("function_name").asText(""))) {
                        fn.setFunctionName(fnNode.path("function_name").asText());
                    }
                    // 解析 AI 输出的 class_paths（不再由程序兜底，但保留 processEntry 的兜底注入逻辑兼容旧数据）
                    mergeClassPaths(fn, fnNode.path("class_paths"));
                    // 解析 AI 输出的 method_signatures
                    mergeMethodSignatures(fn, fnNode.path("method_signatures"));
                }
            }
        }

        // 兼容：AI 输出的 modules[].functions 直接挂在 module 下时（罕见），归到默认 sub_module
        // 当前需求不覆盖此情况，保持简单
    }

    private void mergeKeywords(List<String> existing, JsonNode keywordsNode) {
        if (existing == null || !keywordsNode.isArray()) return;
        for (JsonNode kw : keywordsNode) {
            String v = kw.asText();
            if (StringUtils.hasText(v) && !existing.contains(v)) {
                existing.add(v);
            }
        }
    }

    /**
     * 合并 AI 输出的 class_paths 到 FunctionDto.classPaths
     * null/空/非数组都安全忽略；trim 去除多余空白
     */
    private void mergeClassPaths(FunctionDto fn, JsonNode classPathsNode) {
        if (fn == null || classPathsNode == null || !classPathsNode.isArray()) return;
        for (JsonNode cp : classPathsNode) {
            String v = cp.asText();
            if (StringUtils.hasText(v)) {
                fn.getClassPaths().add(v.trim());
            }
        }
    }

    /**
     * 合并 AI 输出的 method_signatures 到 FunctionDto.methodSignatures
     * 格式约束：methodName(ParamType1, ParamType2)，不含返回类型
     * null/空/非数组都安全忽略；trim 去除多余空白
     */
    private void mergeMethodSignatures(FunctionDto fn, JsonNode methodSignaturesNode) {
        if (fn == null || methodSignaturesNode == null || !methodSignaturesNode.isArray()) return;
        for (JsonNode sig : methodSignaturesNode) {
            String v = sig.asText();
            if (StringUtils.hasText(v)) {
                fn.getMethodSignatures().add(v.trim());
            }
        }
    }

    private FunctionDto findFunctionById(ModuleHierarchy hierarchy, String functionId) {
        for (ModuleDto m : hierarchy.getModules().values()) {
            for (SubModuleDto sm : m.getSubModules().values()) {
                FunctionDto fn = sm.getFunctions().get(functionId);
                if (fn != null) return fn;
            }
        }
        return null;
    }

    /**
     * 全量重写：先 deleteByTaskId，再把 DTO 树展开为 3 行结构批量 insert
     */
    private void persistAll(Long taskId, Long systemId, ModuleHierarchy hierarchy) {
        nodeMapper.delete(new LambdaQueryWrapper<ModuleHierarchyNode>().eq(ModuleHierarchyNode::getTaskId, taskId));
        if (hierarchy.getModules().isEmpty()) {
            return;
        }
        List<ModuleHierarchyNode> rows = new ArrayList<>();
        for (ModuleDto m : hierarchy.getModules().values()) {
            ModuleHierarchyNode modRow = new ModuleHierarchyNode();
            modRow.setTaskId(taskId);
            modRow.setSystemId(systemId);
            modRow.setLevel(LEVEL_MODULE);
            modRow.setParentId(null);
            modRow.setNodeId(m.getId());
            modRow.setName(m.getModuleName());
            modRow.setKeywords(serializeJsonArray(m.getKeywords()));
            modRow.setClassPaths(null);
            rows.add(modRow);
        }
        nodeMapper.insert(rows);
        // 拿到 module 行 ID 后才能填 sub_module.parentId
        Map<String, Long> moduleRowIdByNodeId = new HashMap<>();
        // 重新查一遍（按 nodeId 索引）
        List<ModuleHierarchyNode> insertedModules = nodeMapper.selectList(
                new LambdaQueryWrapper<ModuleHierarchyNode>()
                        .eq(ModuleHierarchyNode::getTaskId, taskId)
                        .eq(ModuleHierarchyNode::getLevel, LEVEL_MODULE)
        );
        for (ModuleHierarchyNode n : insertedModules) {
            moduleRowIdByNodeId.put(n.getNodeId(), n.getId());
        }

        List<ModuleHierarchyNode> subRows = new ArrayList<>();
        Set<String> seenSubNodeIds = new HashSet<>();
        for (ModuleDto m : hierarchy.getModules().values()) {
            Long parentRowId = moduleRowIdByNodeId.get(m.getId());
            if (parentRowId == null) continue;
            for (SubModuleDto sm : m.getSubModules().values()) {
                if (!seenSubNodeIds.add(sm.getId())) {
                    log.warn("persistAll 跳过重复子模块 node_id={}, taskId={}", sm.getId(), taskId);
                    continue;
                }
                ModuleHierarchyNode subRow = new ModuleHierarchyNode();
                subRow.setTaskId(taskId);
                subRow.setSystemId(systemId);
                subRow.setLevel(LEVEL_SUB_MODULE);
                subRow.setParentId(parentRowId);
                subRow.setNodeId(sm.getId());
                subRow.setName(sm.getSubModuleName());
                subRow.setKeywords(serializeJsonArray(sm.getKeywords()));
                subRow.setClassPaths(null);
                subRows.add(subRow);
            }
        }
        if (!subRows.isEmpty()) {
            nodeMapper.insert(subRows);
        }

        // 重新查 sub_module 行 ID
        Map<String, Long> subModuleRowIdByNodeId = new HashMap<>();
        List<ModuleHierarchyNode> insertedSubs = nodeMapper.selectList(
                new LambdaQueryWrapper<ModuleHierarchyNode>()
                        .eq(ModuleHierarchyNode::getTaskId, taskId)
                        .eq(ModuleHierarchyNode::getLevel, LEVEL_SUB_MODULE)
        );
        for (ModuleHierarchyNode n : insertedSubs) {
            subModuleRowIdByNodeId.put(n.getNodeId(), n.getId());
        }

        List<ModuleHierarchyNode> fnRows = new ArrayList<>();
        Set<String> seenFunctionNodeIds = new HashSet<>();
        for (ModuleDto m : hierarchy.getModules().values()) {
            for (SubModuleDto sm : m.getSubModules().values()) {
                Long parentRowId = subModuleRowIdByNodeId.get(sm.getId());
                if (parentRowId == null) continue;
                for (FunctionDto fn : sm.getFunctions().values()) {
                    if (!seenFunctionNodeIds.add(fn.getId())) {
                        log.warn("persistAll 跳过重复功能 node_id={}, taskId={}", fn.getId(), taskId);
                        continue;
                    }
                    ModuleHierarchyNode fnRow = new ModuleHierarchyNode();
                    fnRow.setTaskId(taskId);
                    fnRow.setSystemId(systemId);
                    fnRow.setLevel(LEVEL_FUNCTION);
                    fnRow.setParentId(parentRowId);
                    fnRow.setNodeId(fn.getId());
                    fnRow.setName(fn.getFunctionName());
                    fnRow.setKeywords(null);
                    fnRow.setClassPaths(serializeJsonArray(new ArrayList<>(fn.getClassPaths())));
                    fnRow.setMethodSignatures(serializeJsonArray(new ArrayList<>(fn.getMethodSignatures())));
                    fnRows.add(fnRow);
                }
            }
        }
        if (!fnRows.isEmpty()) {
            nodeMapper.insert(fnRows);
        }

        log.info("persistAll done. taskId={} modules={} subModules={} functions={}",
                taskId, rows.size(), subRows.size(), fnRows.size());
    }

    private List<String> parseJsonArray(String json) {
        if (!StringUtils.hasText(json)) return new ArrayList<>();
        try {
            JsonNode arr = objectMapper.readTree(json);
            List<String> out = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String v = n.asText();
                    if (StringUtils.hasText(v)) out.add(v);
                }
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String serializeJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    private String serializeHierarchyToJson(ModuleHierarchy hierarchy) {
        // 仅供提示词渲染：剥离 class_paths，避免敏感类路径泄漏到大模型调用 payload
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(PromptViewDtos.from(hierarchy));
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 校验前端手工编辑后的层级树：
     * - 节点 ID 必须符合 Base62Generator 约定（m/s/f 前缀 + 5 位）
     * - 名称非空（模块 / 子模块 / 功能）
     * - 子模块挂在模块下，功能挂在子模块下，结构不能错位
     * - 同一 task 内 ID 不能重复
     * - classPaths 中允许为空，但元素必须是字符串
     */
    private void validateReplacement(Long taskId, ModuleHierarchy hierarchy) {
        if (hierarchy == null) {
            throw new BusinessException("模块层级为空");
        }
        Set<String> seenModuleIds = new HashSet<>();
        for (ModuleDto m : hierarchy.getModules().values()) {
            if (!StringUtils.hasText(m.getId()) || !m.getId().startsWith("m") || m.getId().length() != 5) {
                throw new BusinessException("模块 ID 非法: " + m.getId());
            }
            if (!seenModuleIds.add(m.getId())) {
                throw new BusinessException("模块 ID 重复: " + m.getId());
            }
            if (!StringUtils.hasText(m.getModuleName())) {
                throw new BusinessException("模块名称不能为空, id=" + m.getId());
            }
            Set<String> seenSubIds = new HashSet<>();
            for (SubModuleDto sm : m.getSubModules().values()) {
                if (!StringUtils.hasText(sm.getId()) || !sm.getId().startsWith("s") || sm.getId().length() != 5) {
                    throw new BusinessException("子模块 ID 非法: " + sm.getId());
                }
                if (!seenSubIds.add(sm.getId())) {
                    throw new BusinessException("子模块 ID 重复: " + sm.getId());
                }
                if (!StringUtils.hasText(sm.getSubModuleName())) {
                    throw new BusinessException("子模块名称不能为空, id=" + sm.getId());
                }
                Set<String> seenFnIds = new HashSet<>();
                for (FunctionDto fn : sm.getFunctions().values()) {
                    if (!StringUtils.hasText(fn.getId()) || !fn.getId().startsWith("f") || fn.getId().length() != 5) {
                        throw new BusinessException("功能 ID 非法: " + fn.getId());
                    }
                    if (!seenFnIds.add(fn.getId())) {
                        throw new BusinessException("功能 ID 重复: " + fn.getId());
                    }
                    if (!StringUtils.hasText(fn.getFunctionName())) {
                        throw new BusinessException("功能名称不能为空, id=" + fn.getId());
                    }
                    if (fn.getClassPaths() == null) {
                        fn.setClassPaths(new LinkedHashSet<>());
                    }
                    if (fn.getMethodSignatures() == null) {
                        fn.setMethodSignatures(new LinkedHashSet<>());
                    }
                    for (String cp : fn.getClassPaths()) {
                        if (!StringUtils.hasText(cp)) {
                            throw new BusinessException("功能 classPaths 含空元素, id=" + fn.getId());
                        }
                    }
                }
            }
        }
    }

    @Override
    public ModuleHierarchy replaceHierarchy(Long taskId, ModuleHierarchy replacement) {
        if (taskId == null) {
            throw new BusinessException("taskId 不能为空");
        }
        DecompileTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        validateReplacement(taskId, replacement);

        // 设置上下文后落表（persistAll 依赖 taskId / systemId）
        replacement.setTaskId(taskId);
        replacement.setSystemId(task.getSystemId());
        persistAll(taskId, task.getSystemId(), replacement);
        log.info("replaceHierarchy done. taskId={} modules={} functions={}",
                taskId, replacement.getModules().size(), countFunctions(replacement));
        return replacement;
    }

    private String readOptionalFile(java.nio.file.Path path) {
        if (path == null || !Files.exists(path)) return "";
        try {
            return Files.readString(path);
        } catch (Exception e) {
            return "";
        }
    }

    private String stripCodeFence(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline > 0) t = t.substring(firstNewline + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    private int countFunctions(ModuleHierarchy hierarchy) {
        int c = 0;
        for (ModuleDto m : hierarchy.getModules().values()) {
            for (SubModuleDto sm : m.getSubModules().values()) {
                c += sm.getFunctions().size();
            }
        }
        return c;
    }
}