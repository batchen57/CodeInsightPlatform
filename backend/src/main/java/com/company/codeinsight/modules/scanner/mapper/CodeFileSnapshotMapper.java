package com.company.codeinsight.modules.scanner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.scanner.entity.CodeFileSnapshot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代码扫描文件快照数据持久层 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，实现对 ci_file_snapshot 数据库表的常规单表 CRUD。
 */
@Mapper
public interface CodeFileSnapshotMapper extends BaseMapper<CodeFileSnapshot> {
}

