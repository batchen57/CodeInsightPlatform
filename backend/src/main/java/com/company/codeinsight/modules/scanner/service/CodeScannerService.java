package com.company.codeinsight.modules.scanner.service;

import com.company.codeinsight.modules.scanner.entity.CodeFileSnapshot;
import java.io.File;
import java.util.List;

/**
 * 代码拉取与静态文件扫描服务接口
 * 负责定义克隆 Git 仓库、递归遍历工程目录、执行黑白名单过滤以提取核心源文件快照的任务逻辑。
 */
public interface CodeScannerService {

    /**
     * 拉取代码库、扫描目录、过滤无效文件（如图片、第三方包），计算 MD5 指纹并生成快照存入 ci_file_snapshot 数据库表中
     *
     * @param taskId       关联的任务 ID
     * @param repositoryId 关联的代码库配置 ID
     * @return 最终拉取并克隆到本地的临时代码仓库根目录 File 对象
     */
    File pullAndScan(Long taskId, Long repositoryId);

    /**
     * 获取指定分析任务下生成的全部代码文件快照元数据列表
     *
     * @param taskId 任务 ID
     * @return 文件快照列表
     */
    List<CodeFileSnapshot> getSnapshotsByTaskId(Long taskId);
}

