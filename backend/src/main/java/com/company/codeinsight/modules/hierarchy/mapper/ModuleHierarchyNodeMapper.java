package com.company.codeinsight.modules.hierarchy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.hierarchy.entity.ModuleHierarchyNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模块层级节点 Mapper
 */
@Mapper
public interface ModuleHierarchyNodeMapper extends BaseMapper<ModuleHierarchyNode> {
}