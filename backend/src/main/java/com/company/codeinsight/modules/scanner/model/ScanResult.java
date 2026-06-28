package com.company.codeinsight.modules.scanner.model;

import java.io.File;

/**
 * 代码扫描结果
 * <p>
 * 把 {@code pullAndScan} 的产出打包：本地项目目录（供下游 AST 解析、BFS 等用）
 * 加增量上下文（供下游识别「需要重跑」与「保留旧产物」的文件清单）。
 */
public final class ScanResult {

    private final File projectDir;
    private final IncrementalContext incrementalContext;

    public ScanResult(File projectDir, IncrementalContext incrementalContext) {
        this.projectDir = projectDir;
        this.incrementalContext = incrementalContext == null ? IncrementalContext.fullScan() : incrementalContext;
    }

    public File getProjectDir() {
        return projectDir;
    }

    public IncrementalContext getIncrementalContext() {
        return incrementalContext;
    }
}
