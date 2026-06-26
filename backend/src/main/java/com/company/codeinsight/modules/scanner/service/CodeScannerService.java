package com.company.codeinsight.modules.scanner.service;

import com.company.codeinsight.modules.scanner.entity.CodeFileSnapshot;
import java.io.File;
import java.util.List;

public interface CodeScannerService {

    /**
     * 拉取代码库、扫描目录、过滤文件并生成快照存入数据库
     *
     * @return 最终拉取并解压/克隆的本地根目录 File
     */
    File pullAndScan(Long taskId, Long repositoryId);

    /**
     * 获取某次任务下的所有文件快照
     */
    List<CodeFileSnapshot> getSnapshotsByTaskId(Long taskId);
}
