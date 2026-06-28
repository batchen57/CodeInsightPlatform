package com.company.codeinsight.common.util;

import com.company.codeinsight.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 草稿物理文件路径解析工具
 * <p>
 * 数据库中保存的 {@code ci_knowledge_draft.content_uri} 多数是相对路径
 * （例如 {@code storage/drafts/3/draft-service.md}），不能直接传给
 * {@link java.net.URI#create(String)}（只接受带 scheme 的绝对 URI，否则抛
 * {@code IllegalArgumentException: URI is not absolute}）。本工具统一解析为
 * 以 {@code storageLocalPath} 为基址的绝对路径；若 URI 已带 {@code file:}
 * 前缀则直接返回。
 * </p>
 */
public final class DraftFileUtil {

    private DraftFileUtil() {
    }

    /**
     * 把草稿的 contentUri 解析为文件系统绝对路径。
     *
     * @param contentUri        数据库中的 content_uri 字段
     * @param storageLocalPath  storage 根目录（{@code code-insight.storage.local-path}）
     * @return 物理文件绝对路径
     */
    public static Path resolveDraftPath(String contentUri, String storageLocalPath) {
        if (!StringUtils.hasText(contentUri)) {
            throw new BusinessException("草稿 content_uri 为空");
        }
        String trimmed = contentUri.trim();
        // 已经是 file:// 形式的绝对 URI
        if (trimmed.startsWith("file:") || trimmed.startsWith("FILE:")) {
            return Paths.get(URI.create(trimmed));
        }
        // 相对路径 → 以 storageLocalPath 为基址拼接
        Path base = Paths.get(storageLocalPath == null ? "./storage" : storageLocalPath)
                .toAbsolutePath()
                .normalize();
        return base.resolve(trimmed).normalize();
    }
}