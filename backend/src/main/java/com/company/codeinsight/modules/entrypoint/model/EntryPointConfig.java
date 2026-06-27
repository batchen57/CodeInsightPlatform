package com.company.codeinsight.modules.entrypoint.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务级入口扫描配置（配置只跟任务绑定，不跟仓库绑定）
 *
 * - include 规则：满足任一即视为入口候选
 * - exclude 规则：满足任一即从候选中排除
 *
 * 任一 include 列表为空且 config 不为 null → 走默认 Controller/JOB/MQ 兜底
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntryPointConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /* ---------- 入口识别（满足任一即匹配）---------- */

    /** 入口注解列表：类的 annotations 含任一元素即匹配（简单 contains） */
    private List<String> includeAnnotations = new ArrayList<>();

    /** 入口类路径（Ant 风格）列表：FQ 与任一模式匹配即匹配 */
    private List<String> includeClasspaths = new ArrayList<>();

    /** 入口继承/实现列表：extendsClass 或 implementsList 含任一元素即匹配 */
    private List<String> includeExtends = new ArrayList<>();

    /* ---------- 排除规则（满足任一即排除）---------- */

    /** 排除类路径（Ant 风格）列表 */
    private List<String> excludeClasspaths = new ArrayList<>();

    /** 排除包路径列表：FQ.startsWith 任一元素（点分隔）即匹配 */
    private List<String> excludePackages = new ArrayList<>();

    /** 排除注解列表：annotations 含任一元素即匹配 */
    private List<String> excludeAnnotations = new ArrayList<>();

    /* ---------- 安全 getter（null → 空列表）---------- */

    public List<String> getEffectiveIncludeAnnotations() {
        return includeAnnotations == null ? List.of() : includeAnnotations;
    }

    public List<String> getEffectiveIncludeClasspaths() {
        return includeClasspaths == null ? List.of() : includeClasspaths;
    }

    public List<String> getEffectiveIncludeExtends() {
        return includeExtends == null ? List.of() : includeExtends;
    }

    public List<String> getEffectiveExcludeClasspaths() {
        return excludeClasspaths == null ? List.of() : excludeClasspaths;
    }

    public List<String> getEffectiveExcludePackages() {
        return excludePackages == null ? List.of() : excludePackages;
    }

    public List<String> getEffectiveExcludeAnnotations() {
        return excludeAnnotations == null ? List.of() : excludeAnnotations;
    }

    /** 三个 include 列表是否都为空（用于走默认行为兜底） */
    public boolean isIncludeAllEmpty() {
        return getEffectiveIncludeAnnotations().isEmpty()
                && getEffectiveIncludeClasspaths().isEmpty()
                && getEffectiveIncludeExtends().isEmpty();
    }
}