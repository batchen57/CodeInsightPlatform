package com.company.codeinsight.modules.entrypoint.service.impl;

import com.company.codeinsight.modules.callchain.entity.MethodCall;
import com.company.codeinsight.modules.callchain.service.MethodCallService;
import com.company.codeinsight.modules.entrypoint.model.EntryPoint;
import com.company.codeinsight.modules.entrypoint.model.EntryPointConfig;
import com.company.codeinsight.modules.entrypoint.service.EntryPointDiscoveryService;
import com.company.codeinsight.modules.parser.model.ParsedClassInfo;
import com.company.codeinsight.modules.parser.service.JavaParserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 入口识别服务实现
 * - 默认行为（config 为 null 或三个 include 都空）：沿用 Controller/JOB/MQ/main 兜底
 * - 配置驱动：按 EntryPointConfig 的 6 类规则"或"逻辑匹配 + 排除传染
 */
@Slf4j
@Service
public class EntryPointDiscoveryServiceImpl implements EntryPointDiscoveryService {

    @Autowired
    private JavaParserService javaParserService;

    @Autowired
    private MethodCallService methodCallService;

    /** Ant 路径匹配器（无状态，可复用） */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public List<EntryPoint> discoverEntries(Long taskId, File projectDir) {
        return discoverEntries(taskId, projectDir, null);
    }

    @Override
    public List<EntryPoint> discoverEntries(Long taskId, File projectDir, EntryPointConfig config) {
        return discoverEntriesInternal(taskId, projectDir, config);
    }

    @Override
    public String collectReachableSource(Long taskId, String entryClassName, File projectDir) {
        return collectReachableSource(taskId, entryClassName, projectDir, null);
    }

    @Override
    public String collectReachableSource(Long taskId, String entryClassName, File projectDir, EntryPointConfig config) {
        return collectReachableSourceInternal(taskId, entryClassName, projectDir, config);
    }

    // ============================ core impl ============================

    private List<EntryPoint> discoverEntriesInternal(Long taskId, File projectDir, EntryPointConfig config) {
        if (taskId == null || projectDir == null || !projectDir.exists()) {
            return Collections.emptyList();
        }

        // 1. 解析全项目
        List<ParsedClassInfo> parsed;
        try {
            parsed = javaParserService.parseDirectory(projectDir);
        } catch (Exception e) {
            log.error("parseDirectory failed for task {}", taskId, e);
            return Collections.emptyList();
        }

        // 2. 加载调用链，构建 dependency_name -> [file_path] 反向索引
        List<MethodCall> calls = methodCallService.listByTaskId(taskId);
        Map<String, String> depNameToFilePath = new HashMap<>();
        for (MethodCall mc : calls) {
            if (StringUtils.hasText(mc.getDependencyName()) && StringUtils.hasText(mc.getFilePath())) {
                depNameToFilePath.putIfAbsent(mc.getDependencyName(), mc.getFilePath());
            }
        }
        Set<String> calledDependencies = depNameToFilePath.keySet();

        boolean useDefault = (config == null) || config.isIncludeAllEmpty();

        // 3. 主路：默认行为 OR 配置驱动
        Map<String, EntryPoint> entries = new LinkedHashMap<>();
        Map<String, ParsedClassInfo> classNameToInfo = new HashMap<>();
        for (ParsedClassInfo info : parsed) {
            if (info == null || !StringUtils.hasText(info.getClassName())) {
                continue;
            }
            String fq = fqName(info);
            classNameToInfo.put(fq, info);

            // 排除优先：命中任一排除规则即跳过
            if (isExcluded(fq, info, config)) {
                continue;
            }

            if (useDefault) {
                String type = info.getType();
                String entryType = mapTypeToEntryType(type);
                if (entryType != null) {
                    addEntry(entries, fq, info, entryType, extractTriggerAnnotation(info.getAnnotations(), entryType));
                } else if (info.isHasMainMethod()) {
                    addEntry(entries, fq, info, "APPLICATION", "main");
                }
            } else {
                // 配置驱动：三 include"或"逻辑
                boolean hit = matchesIncludeAnnotation(info, config)
                        || matchesIncludeClasspath(fq, config)
                        || matchesIncludeExtends(info, config);
                if (hit) {
                    String entryType = mapTypeToEntryType(info.getType());
                    if (entryType == null) {
                        entryType = "CUSTOM";
                    }
                    addEntry(entries, fq, info, entryType, firstHitAnnotation(info, config));
                }
            }
        }

        // 4. 辅路：调用链反查（仅默认行为 + 带 main 的入口，避免配置驱动时把 main() 漏掉）
        if (useDefault) {
            Set<String> allClasses = new HashSet<>(classNameToInfo.keySet());
            Set<String> zeroRefClasses = new HashSet<>(allClasses);
            zeroRefClasses.removeAll(calledDependencies);

            for (String fq : zeroRefClasses) {
                if (entries.containsKey(fq)) {
                    continue;
                }
                ParsedClassInfo info = classNameToInfo.get(fq);
                if (info != null && info.isHasMainMethod()) {
                    addEntry(entries, fq, info, "MAIN", "main (zero-reference)");
                }
            }
        }

        log.info("Entry point discovery done. taskId={}, entriesFound={}, useDefault={}", taskId, entries.size(), useDefault);
        return new ArrayList<>(entries.values());
    }

    private String collectReachableSourceInternal(Long taskId, String entryClassName, File projectDir, EntryPointConfig config) {
        if (taskId == null || !StringUtils.hasText(entryClassName) || projectDir == null || !projectDir.exists()) {
            return "";
        }

        // 入口自身若命中排除规则 → 直接返回空
        ParsedClassInfo entryInfo = null;
        try {
            List<ParsedClassInfo> all = javaParserService.parseDirectory(projectDir);
            for (ParsedClassInfo p : all) {
                if (entryClassName.endsWith("." + p.getClassName())
                        || entryClassName.equals(p.getClassName())
                        || entryClassName.equals(fqName(p))) {
                    entryInfo = p;
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        if (entryInfo != null && isExcluded(entryClassName, entryInfo, config)) {
            return "";
        }

        List<MethodCall> calls = methodCallService.listByTaskId(taskId);
        if (calls == null || calls.isEmpty()) {
            return readSourceFile(projectDir, lookupFilePathByClass(calls, entryClassName), entryClassName);
        }

        // BFS：从入口类出发，遍历 dependencyName 找到可达类集合
        Map<String, Set<String>> classToDeps = new HashMap<>();
        Map<String, String> classToFilePath = new HashMap<>();
        for (MethodCall mc : calls) {
            if (!StringUtils.hasText(mc.getClassName())) {
                continue;
            }
            String depType = stripVariableFromDependencyName(mc.getDependencyName());
            if (StringUtils.hasText(depType)) {
                classToDeps.computeIfAbsent(mc.getClassName(), k -> new LinkedHashSet<>()).add(depType);
            }
            if (StringUtils.hasText(mc.getFilePath())) {
                classToFilePath.putIfAbsent(mc.getClassName(), mc.getFilePath());
            }
        }
        String entryShortName = entryClassName.contains(".") ? entryClassName.substring(entryClassName.lastIndexOf('.') + 1) : entryClassName;
        classToFilePath.putIfAbsent(entryShortName, lookupFilePathByClass(calls, entryClassName));

        Set<String> reachable = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(entryShortName);
        reachable.add(entryShortName);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            Set<String> deps = classToDeps.get(cur);
            if (deps == null) continue;
            for (String dep : deps) {
                if (reachable.contains(dep)) continue;
                // 排除传染：邻居依赖命中排除规则则不展开
                ParsedClassInfo depInfo = null;
                try {
                    List<ParsedClassInfo> all = javaParserService.parseDirectory(projectDir);
                    for (ParsedClassInfo p : all) {
                        if (p.getClassName() != null && p.getClassName().equals(dep)) {
                            depInfo = p;
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
                if (depInfo != null) {
                    String depFq = fqName(depInfo);
                    if (isExcluded(depFq, depInfo, config)) {
                        continue;
                    }
                }
                reachable.add(dep);
                queue.add(dep);
            }
        }

        // 拼接源码
        StringBuilder sb = new StringBuilder();
        for (String className : reachable) {
            String relPath = classToFilePath.get(className);
            if (relPath == null) {
                relPath = lookupFilePathByClass(calls, className);
            }
            String content = readSourceFile(projectDir, relPath, className);
            sb.append("// === Class: ").append(className).append(" ===\n");
            sb.append(content).append("\n\n");
        }
        return sb.toString();
    }

    // ============================ rule helpers ============================

    private boolean matchesIncludeAnnotation(ParsedClassInfo info, EntryPointConfig cfg) {
        List<String> rules = cfg.getEffectiveIncludeAnnotations();
        if (rules.isEmpty() || info.getAnnotations() == null) return false;
        for (String ann : info.getAnnotations()) {
            for (String rule : rules) {
                if (ann != null && rule != null && ann.contains(rule)) return true;
            }
        }
        return false;
    }

    private boolean matchesIncludeClasspath(String fq, EntryPointConfig cfg) {
        for (String pattern : cfg.getEffectiveIncludeClasspaths()) {
            if (StringUtils.hasText(pattern) && pathMatcher.match(pattern, fq)) return true;
        }
        return false;
    }

    private boolean matchesIncludeExtends(ParsedClassInfo info, EntryPointConfig cfg) {
        List<String> rules = cfg.getEffectiveIncludeExtends();
        if (rules.isEmpty()) return false;
        if (StringUtils.hasText(info.getExtendsClass())) {
            for (String r : rules) if (r != null && r.equals(info.getExtendsClass())) return true;
        }
        if (info.getImplementsList() != null) {
            for (String impl : info.getImplementsList()) {
                for (String r : rules) if (r != null && r.equals(impl)) return true;
            }
        }
        return false;
    }

    /**
     * 三类排除规则"或"逻辑：任一命中即排除
     */
    private boolean isExcluded(String fq, ParsedClassInfo info, EntryPointConfig cfg) {
        if (cfg == null) return false;

        for (String p : cfg.getEffectiveExcludeClasspaths()) {
            if (StringUtils.hasText(p) && pathMatcher.match(p, fq)) return true;
        }
        for (String pkg : cfg.getEffectiveExcludePackages()) {
            if (StringUtils.hasText(pkg)) {
                String prefix = pkg.endsWith(".") ? pkg : pkg + ".";
                if (fq.startsWith(prefix) || fq.equals(pkg)) return true;
            }
        }
        if (info != null && info.getAnnotations() != null) {
            for (String ann : info.getAnnotations()) {
                for (String rule : cfg.getEffectiveExcludeAnnotations()) {
                    if (ann != null && rule != null && ann.contains(rule)) return true;
                }
            }
        }
        return false;
    }

    private String firstHitAnnotation(ParsedClassInfo info, EntryPointConfig cfg) {
        if (info.getAnnotations() == null) return null;
        for (String ann : info.getAnnotations()) {
            for (String rule : cfg.getEffectiveIncludeAnnotations()) {
                if (ann != null && rule != null && ann.contains(rule)) return ann;
            }
        }
        return null;
    }

    // ============================ entry builder ============================

    private void addEntry(Map<String, EntryPoint> entries, String fq, ParsedClassInfo info, String entryType, String annotation) {
        if (entries.containsKey(fq)) return;
        EntryPoint ep = new EntryPoint();
        ep.setClassName(fq);
        ep.setFilePath(lookupFilePath(info.getClassName(), null, info));
        ep.setEntryType(entryType);
        ep.setAnnotation(annotation);
        ep.setRemark(info.getRequestMapping());
        entries.put(fq, ep);
    }

    private String mapTypeToEntryType(String type) {
        if (type == null) return null;
        return switch (type) {
            case "CONTROLLER" -> "CONTROLLER";
            case "JOB" -> "SCHEDULED_JOB";
            case "MESSAGE_LISTENER" -> "MQ_LISTENER";
            case "COMPONENT" -> "COMPONENT";
            case "APPLICATION" -> "APPLICATION";
            default -> null;
        };
    }

    private String extractTriggerAnnotation(List<String> annotations, String entryType) {
        if (annotations == null || annotations.isEmpty()) return null;
        return switch (entryType) {
            case "CONTROLLER" -> annotations.stream().filter(a -> a.contains("Controller")).findFirst().orElse(null);
            case "SCHEDULED_JOB" -> annotations.stream().filter(a -> a.contains("Scheduled") || a.contains("EnableScheduling")).findFirst().orElse(null);
            case "MQ_LISTENER" -> annotations.stream().filter(a -> a.contains("Listener")).findFirst().orElse(null);
            case "COMPONENT" -> annotations.stream().filter(a -> a.equals("Component")).findFirst().orElse(null);
            case "APPLICATION" -> annotations.stream().filter(a -> a.contains("SpringBootApplication")).findFirst().orElse(null);
            default -> null;
        };
    }

    // ============================ low-level helpers ============================

    private String fqName(ParsedClassInfo info) {
        if (!StringUtils.hasText(info.getPackageName())) return info.getClassName();
        return info.getPackageName() + "." + info.getClassName();
    }

    private String lookupFilePath(String className, List<ParsedClassInfo> parsed, ParsedClassInfo info) {
        // 优先用 info 的 packageName 推断
        if (info != null && StringUtils.hasText(info.getPackageName())) {
            return "src/main/java/" + info.getPackageName().replace('.', '/') + "/" + className + ".java";
        }
        return null;
    }

    private String lookupFilePathByClass(List<MethodCall> calls, String className) {
        if (calls == null || !StringUtils.hasText(className)) {
            return null;
        }
        String shortName = className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;
        for (MethodCall mc : calls) {
            if (shortName.equals(mc.getClassName()) && StringUtils.hasText(mc.getFilePath())) {
                return mc.getFilePath();
            }
        }
        return null;
    }

    private String stripVariableFromDependencyName(String dependencyName) {
        if (!StringUtils.hasText(dependencyName)) return null;
        int colonIdx = dependencyName.indexOf(':');
        return colonIdx >= 0 ? dependencyName.substring(colonIdx + 1) : dependencyName;
    }

    private String readSourceFile(File projectDir, String relativePath, String fallbackClassName) {
        if (projectDir == null) return "// (no source: projectDir null)";
        File target = null;
        if (StringUtils.hasText(relativePath)) {
            target = new File(projectDir, relativePath.replace('/', File.separatorChar));
        }
        if (target == null || !target.exists()) {
            if (StringUtils.hasText(fallbackClassName) && fallbackClassName.contains(".")) {
                String pkgPath = fallbackClassName.substring(0, fallbackClassName.lastIndexOf('.')).replace('.', '/');
                String simpleName = fallbackClassName.substring(fallbackClassName.lastIndexOf('.') + 1);
                target = new File(projectDir, "src/main/java/" + pkgPath + "/" + simpleName + ".java");
            }
        }
        if (target != null && target.exists()) {
            try {
                return Files.readString(target.toPath());
            } catch (Exception e) {
                log.warn("read source failed: {}", target.getAbsolutePath(), e);
            }
        }
        return "// (source not found: " + (target == null ? "null" : target.getAbsolutePath()) + ")";
    }
}