package com.company.codeinsight.common.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 提示词模板加载与渲染工具
 * 从 classpath 加载 Markdown 模板（如 analyze_prompt.md），并替换占位符。
 * 占位符大小写敏感，严格匹配模板原文：{java_code} / {business_knowledge.md} / {module_hierarchy.json}
 */
@Component
public class PromptTemplateLoader {

    /**
     * 从 classpath 加载模板文本
     * @param classpathPath 例如 "analyze_prompt.md"
     */
    public String load(String classpathPath) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathPath);
            if (!resource.exists()) {
                throw new IllegalArgumentException("模板文件不存在于 classpath: " + classpathPath);
            }
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取模板文件失败: " + classpathPath, e);
        }
    }

    /**
     * 渲染占位符：{java_code} / {business_knowledge.md} / {module_hierarchy.json}
     * @param template           已加载的模板字符串
     * @param javaCode           待分析的 Java 源码
     * @param businessKnowledge  业务知识库 Markdown 内容（可为空）
     * @param moduleHierarchy    已有 module_hierarchy.json 内容（可为空）
     */
    public String render(String template, String javaCode, String businessKnowledge, String moduleHierarchy) {
        String t = template == null ? "" : template;
        return t
                .replace("{java_code}", javaCode == null ? "" : javaCode)
                .replace("{business_knowledge.md}", businessKnowledge == null ? "" : businessKnowledge)
                .replace("{module_hierarchy.json}", moduleHierarchy == null ? "" : moduleHierarchy);
    }

    /** 简单判断模板里是否还含未替换的占位符（用于校验） */
    public boolean hasUnresolvedPlaceholders(String rendered) {
        return StringUtils.hasText(rendered)
                && (rendered.contains("{java_code}")
                || rendered.contains("{business_knowledge.md}")
                || rendered.contains("{module_hierarchy.json}"));
    }

    /**
     * 渲染 module_doc_prompt.md 模板（项 3 整模块喂 AI 用）
     * 占位符：{公共模块名称} / {module_hierarchy.json} / {java.code}
     */
    public String renderModuleDoc(String template,
                                  String moduleName,
                                  String moduleHierarchyJson,
                                  String javaCode) {
        String t = template == null ? "" : template;
        return t
                .replace("{公共模块名称}", nullSafe(moduleName))
                .replace("{module_hierarchy.json}", nullSafe(moduleHierarchyJson))
                .replace("{java.code}", nullSafe(javaCode));
    }

    /** 判断 module_doc 模板是否还含未替换占位符 */
    public boolean hasUnresolvedModuleDocPlaceholders(String rendered) {
        return StringUtils.hasText(rendered) && (
                rendered.contains("{公共模块名称}")
                        || rendered.contains("{module_hierarchy.json}")
                        || rendered.contains("{java.code}"));
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}