package com.company.codeinsight.modules.chunk.service;

import com.company.codeinsight.modules.chunk.entity.CodeChunk;
import com.company.codeinsight.modules.scanner.entity.CodeFileSnapshot;

import java.util.List;

/**
 * 代码切片管理服务接口
 * 负责定义将扫描的文件快照切分成最小 AI 分析单元的方法，以及切片状态管理和查询。
 */
public interface CodeChunkService {

    /**
     * 将代码文件快照切分成代码分片，并估算 Token 大小，持久化至 ci_chunk 数据表中
     *
     * @param taskId    关联的任务 ID
     * @param snapshots 任务所拉取出的代码文件快照集合
     */
    void chunkAndEstimate(Long taskId, List<CodeFileSnapshot> snapshots);

    /**
     * 获取指定任务生成的全部代码分片
     *
     * @param taskId 任务 ID
     * @return 代码分片实体对象列表
     */
    List<CodeChunk> getChunksByTaskId(Long taskId);

    /**
     * 将某个分片标记为分析失败，记录具体失败异常信息
     *
     * @param chunkId 分片 ID
     * @param reason  失败异常理由说明
     */
    void markChunkFailed(Long chunkId, String reason);

    /**
     * 重新重跑或恢复某个分析失败的切片
     *
     * @param chunkId 分片 ID
     */
    void retryChunk(Long chunkId);
}

