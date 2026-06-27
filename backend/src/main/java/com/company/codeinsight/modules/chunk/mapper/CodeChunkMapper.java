package com.company.codeinsight.modules.chunk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.chunk.entity.CodeChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代码切片数据持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_chunk 数据表的常规 CRUD 与持久化。
 */
@Mapper
public interface CodeChunkMapper extends BaseMapper<CodeChunk> {
}

