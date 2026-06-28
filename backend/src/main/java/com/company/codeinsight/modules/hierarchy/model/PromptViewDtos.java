package com.company.codeinsight.modules.hierarchy.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 提示词专用的模块层级视图 DTO（剥离 class_paths）
 *
 * 用于渲染 analyze_prompt.md / module_doc_prompt.md 中的 {module_hierarchy.json} 占位符：
 * - 与 analyze_prompt.md 给 AI 的 JSON 形状严格一致（id / moduleName / keywords / subModules / functions）
 * - 函数节点不再包含 classPaths——该字段由程序侧注入维护，AI 不应看见，避免污染命名判断 / 增加 token
 *
 * 不要用 Jackson Mixin / @JsonIgnore 隐式过滤。显式的 view 既可读，也避免后续 DTO 字段变更意外泄漏到 prompt。
 */
public final class PromptViewDtos {

    private PromptViewDtos() {
    }

    /** 顶层视图 */
    @Data
    public static class PromptHierarchyView {
        private List<PromptModuleView> modules = new ArrayList<>();
    }

    /** 模块级视图 */
    @Data
    public static class PromptModuleView {
        private String id;
        private String moduleName;
        private List<String> keywords = new ArrayList<>();
        private List<PromptSubModuleView> subModules = new ArrayList<>();
    }

    /** 子模块级视图 */
    @Data
    public static class PromptSubModuleView {
        private String id;
        private String subModuleName;
        private List<String> keywords = new ArrayList<>();
        private List<PromptFunctionView> functions = new ArrayList<>();
    }

    /** 功能级视图（不带 class_paths） */
    @Data
    public static class PromptFunctionView {
        private String id;
        private String functionName;
    }

    /**
     * 从内存中的 ModuleHierarchy 构建提示词视图。
     * 不修改源数据；只读遍历。
     */
    public static PromptHierarchyView from(ModuleHierarchy hierarchy) {
        PromptHierarchyView view = new PromptHierarchyView();
        if (hierarchy == null || hierarchy.getModules() == null) {
            return view;
        }
        for (ModuleDto m : hierarchy.getModules().values()) {
            PromptModuleView mv = new PromptModuleView();
            mv.setId(m.getId());
            mv.setModuleName(m.getModuleName());
            mv.setKeywords(m.getKeywords() == null ? new ArrayList<>() : new ArrayList<>(m.getKeywords()));
            for (SubModuleDto sm : m.getSubModules().values()) {
                PromptSubModuleView smv = new PromptSubModuleView();
                smv.setId(sm.getId());
                smv.setSubModuleName(sm.getSubModuleName());
                smv.setKeywords(sm.getKeywords() == null ? new ArrayList<>() : new ArrayList<>(sm.getKeywords()));
                for (FunctionDto fn : sm.getFunctions().values()) {
                    PromptFunctionView fnv = new PromptFunctionView();
                    fnv.setId(fn.getId());
                    fnv.setFunctionName(fn.getFunctionName());
                    smv.getFunctions().add(fnv);
                }
                mv.getSubModules().add(smv);
            }
            view.getModules().add(mv);
        }
        return view;
    }
}