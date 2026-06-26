package com.company.codeinsight.modules.scanner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.scanner.entity.CodeFileSnapshot;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CodeFileSnapshotMapper extends BaseMapper<CodeFileSnapshot> {
}
