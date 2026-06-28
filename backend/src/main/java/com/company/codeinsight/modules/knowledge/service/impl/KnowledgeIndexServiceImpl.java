package com.company.codeinsight.modules.knowledge.service.impl;

import com.company.codeinsight.modules.draft.entity.KnowledgeDraft;
import com.company.codeinsight.modules.hierarchy.model.FunctionDto;
import com.company.codeinsight.modules.hierarchy.model.ModuleDto;
import com.company.codeinsight.modules.hierarchy.model.ModuleHierarchy;
import com.company.codeinsight.modules.hierarchy.model.SubModuleDto;
import com.company.codeinsight.modules.knowledge.service.KnowledgeIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 三级索引生成实现
 * 输出 Markdown 表格：
 * | 模块 | 子模块 | 功能 | 入口类 | md 链接 |
 * 链接路径：[模块名](modules/模块名.md)，锚点跳转到对应子模块标题
 */
@Slf4j
@Service
public class KnowledgeIndexServiceImpl implements KnowledgeIndexService {

    @Override
    public Path generateModuleIndex(Path modulesPath, ModuleHierarchy hierarchy, List<KnowledgeDraft> drafts) throws IOException {
        // 建立 moduleName → md 文件相对路径索引
        Map<String, String> moduleNameToMdRelPath = new HashMap<>();
        for (KnowledgeDraft draft : drafts) {
            if (StringUtils.hasText(draft.getModuleName()) && StringUtils.hasText(draft.getFilePath())) {
                String fileName = extractFileName(draft.getFilePath());
                if (StringUtils.hasText(fileName)) {
                    moduleNameToMdRelPath.put(draft.getModuleName(), "modules/" + fileName);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# 模块知识归纳索引\n\n");
        sb.append("本知识库由代码洞察平台基于大模型及静态解析自动归纳生成。\n\n");
        sb.append("## 系统模块列表\n\n");

        if (hierarchy == null || hierarchy.getModules() == null || hierarchy.getModules().isEmpty()) {
            // DTO 为空时回退到纯 KnowledgeDraft 列表（与原 KnowledgeServiceImpl 行为兼容）
            appendFallbackList(sb, moduleNameToMdRelPath);
        } else {
            appendThreeLevelTable(sb, hierarchy, moduleNameToMdRelPath);
        }

        sb.append("\n## 文档导览\n");
        sb.append("- [架构概览](architecture-overview.md)\n");
        sb.append("- [接口索引](api-index.md)\n");
        sb.append("- [数据库索引](database-index.md)\n");
        sb.append("- [依赖与调用链路](dependency-index.md)\n");
        sb.append("- [待确认事项清单](pending-confirmation.md)\n");

        Path indexPath = modulesPath.resolve("module-index.md");
        Files.writeString(indexPath, sb.toString());
        log.info("KnowledgeIndexService generated: {}", indexPath);
        return indexPath;
    }

    /**
     * 三级表格：模块 → 子模块 → 功能 → md 链接
     */
    private void appendThreeLevelTable(StringBuilder sb,
                                       ModuleHierarchy hierarchy,
                                       Map<String, String> moduleNameToMdRelPath) {
        sb.append("| 模块 | 子模块 | 功能 | 入口类 | md 链接 |\n");
        sb.append("| --- | --- | --- | --- | --- |\n");

        for (ModuleDto moduleDto : hierarchy.getModules().values()) {
            String moduleName = moduleDto.getModuleName();
            String mdRelPath = moduleNameToMdRelPath.getOrDefault(moduleName, null);

            if (moduleDto.getSubModules() == null || moduleDto.getSubModules().isEmpty()) {
                // 模块没有子模块 → 单行展示
                sb.append(buildTableRow(moduleName, "—", "—", "—", mdRelPath, true, 1, true, 1));
                continue;
            }

            int subModuleCount = moduleDto.getSubModules().size();
            int subIndex = 0;
            for (SubModuleDto subModuleDto : moduleDto.getSubModules().values()) {
                String subName = subModuleDto.getSubModuleName();
                int functionCount = subModuleDto.getFunctions() == null ? 0 : subModuleDto.getFunctions().size();

                if (functionCount == 0) {
                    // 子模块无功能
                    sb.append(buildTableRow(moduleName, subName, "—", "—", mdRelPath, subIndex == 0, subModuleCount, true, 1));
                    subIndex++;
                    continue;
                }

                int fnIndex = 0;
                for (FunctionDto functionDto : subModuleDto.getFunctions().values()) {
                    String fnName = functionDto.getFunctionName();
                    String entries = joinClassPaths(functionDto.getClassPaths());
                    sb.append(buildTableRow(moduleName, subName, fnName, entries, mdRelPath,
                            subIndex == 0, subModuleCount,
                            fnIndex == 0, functionCount));
                    fnIndex++;
                }
                subIndex++;
            }
        }
    }

    /**
     * 降级模式：DTO 为空时只列 KnowledgeDraft 名称与 md 链接（与原 buildLocalDocs 行为一致）
     */
    private void appendFallbackList(StringBuilder sb, Map<String, String> moduleNameToMdRelPath) {
        sb.append("| 模块 | md 链接 |\n| --- | --- |\n");
        // 按模块名排序输出
        Set<String> sorted = new TreeSet<>(moduleNameToMdRelPath.keySet());
        for (String moduleName : sorted) {
            String rel = moduleNameToMdRelPath.get(moduleName);
            sb.append("| ").append(moduleName).append(" | [").append(moduleName).append("](")
                    .append(rel).append(") |\n");
        }
    }

    /**
     * 单行表格（不处理单元格合并，仅展示）。
     * Markdown 不支持单元格合并时退化为重复展示模块/子模块名称以保证可读性。
     */
    private String buildTableRow(String moduleName, String subName, String fnName,
                                 String entries, String mdRelPath,
                                 boolean firstInModule, int moduleSpan,
                                 boolean firstInSub, int subSpan) {
        String linkCell = mdRelPath == null
                ? "—"
                : "[" + escapeMd(moduleName) + "](" + mdRelPath + ")";
        return "| " + escapeMd(moduleName)
                + " | " + escapeMd(subName)
                + " | " + escapeMd(fnName)
                + " | " + escapeMd(entries)
                + " | " + linkCell + " |\n";
    }

    private String joinClassPaths(Set<String> classPaths) {
        if (classPaths == null || classPaths.isEmpty()) {
            return "—";
        }
        return classPaths.stream().sorted().collect(Collectors.joining("<br>"));
    }

    /**
     * 提取 file_path 末段文件名（如 "task_123/UserModule.md" → "UserModule.md"）
     */
    private String extractFileName(String filePath) {
        if (filePath == null) return null;
        int idx = filePath.lastIndexOf('/');
        if (idx < 0) idx = filePath.lastIndexOf('\\');
        return idx >= 0 ? filePath.substring(idx + 1) : filePath;
    }

    /**
     * Markdown 表格内容最小转义：避免竖线 / 换行破坏表格结构
     */
    private String escapeMd(String value) {
        if (value == null) return "—";
        return value.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
    }
}