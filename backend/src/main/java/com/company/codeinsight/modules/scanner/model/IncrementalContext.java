package com.company.codeinsight.modules.scanner.model;

import java.util.Collections;
import java.util.Set;

/**
 * 增量扫描上下文
 * <p>
 * 由 {@code CodeScannerService.pullAndScan} 在扫描完成后产出，被下游
 * PARSING / SPLITTING / AI_ANALYZING / GENERATING_DOC 阶段共享，用于：
 * <ul>
 *   <li>仅重新解析发生变更的文件，保留未变文件的旧产物（方法调用链、代码切片、模块层级）</li>
 *   <li>识别被删除的文件并从下游表中清理对应的行</li>
 * </ul>
 * <p>
 * 全量扫描时构造一个 {@link #fullScan()} 即可，下游用 {@link #isIncremental()} 判定走全量分支。
 */
public final class IncrementalContext {

    private final boolean incremental;
    private final Set<String> changedPaths;
    private final Set<String> deletedPaths;

    private IncrementalContext(boolean incremental, Set<String> changedPaths, Set<String> deletedPaths) {
        this.incremental = incremental;
        this.changedPaths = changedPaths == null ? Collections.emptySet() : Collections.unmodifiableSet(changedPaths);
        this.deletedPaths = deletedPaths == null ? Collections.emptySet() : Collections.unmodifiableSet(deletedPaths);
    }

    /**
     * 构造一个空的全量上下文：所有判定方法返回 false，下游走原全量分支。
     */
    public static IncrementalContext fullScan() {
        return new IncrementalContext(false, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * 构造增量上下文。任一参数为 null 都视为空集合。
     */
    public static IncrementalContext incremental(Set<String> changedPaths, Set<String> deletedPaths) {
        return new IncrementalContext(true, changedPaths, deletedPaths);
    }

    public boolean isIncremental() {
        return incremental;
    }

    public Set<String> getChangedPaths() {
        return changedPaths;
    }

    public Set<String> getDeletedPaths() {
        return deletedPaths;
    }

    /**
     * 给定文件相对路径，判断是否在本次变更集中（增量模式下才有意义）。
     */
    public boolean isPathChanged(String relativePath) {
        return incremental && relativePath != null && changedPaths.contains(relativePath);
    }

    /**
     * 给定文件相对路径，判断是否在本次删除集中（增量模式下才有意义）。
     */
    public boolean isPathDeleted(String relativePath) {
        return incremental && relativePath != null && deletedPaths.contains(relativePath);
    }

    /**
     * 给定文件相对路径，判断是否需要被下游阶段「保留旧产物、不再处理」。
     * 即：增量模式 且 路径不在 changed/deleted 集合内。
     */
    public boolean isPathUnchanged(String relativePath) {
        return incremental
                && relativePath != null
                && !changedPaths.contains(relativePath)
                && !deletedPaths.contains(relativePath);
    }

    @Override
    public String toString() {
        return "IncrementalContext{incremental=" + incremental
                + ", changed=" + changedPaths.size()
                + ", deleted=" + deletedPaths.size() + "}";
    }
}
