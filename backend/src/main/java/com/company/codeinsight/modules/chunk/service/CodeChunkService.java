package com.company.codeinsight.modules.chunk.service;

import com.company.codeinsight.modules.chunk.entity.CodeChunk;
import com.company.codeinsight.modules.scanner.entity.CodeFileSnapshot;

import java.util.List;

public interface CodeChunkService {

    void chunkAndEstimate(Long taskId, List<CodeFileSnapshot> snapshots);

    List<CodeChunk> getChunksByTaskId(Long taskId);

    void markChunkFailed(Long chunkId, String reason);

    void retryChunk(Long chunkId);
}
