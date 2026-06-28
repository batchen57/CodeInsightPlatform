package com.company.codeinsight.modules.push.enums;

/**
 * 知识推送方式枚举
 *
 * <pre>
 * GIT - 通过 JGit 推送文档到远程 Git 仓库的指定文件夹
 * S3  - 压缩文档上传到 S3 兼容对象存储并替换云端内容（预留）
 * </pre>
 */
public enum PushMethod {

    /** Git 推送：将知识文档推送到远程 Git 仓库指定分支和文件夹 */
    GIT,

    /** S3 对象存储推送：压缩文档上传到 S3 存储桶（接口预留，暂未实现） */
    S3
}
