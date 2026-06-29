package com.company.codeinsight.modules.entrypoint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.codeinsight.modules.entrypoint.entity.EntrypointEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识入口复核表 mapper（对应 ci_entrypoint）
 */
@Mapper
public interface EntrypointMapper extends BaseMapper<EntrypointEntity> {

    /**
     * 按 taskId 列出所有入口行（按 sort_order, id 升序）
     */
    @Select("SELECT * FROM ci_entrypoint WHERE task_id = #{taskId} ORDER BY sort_order ASC, id ASC")
    List<EntrypointEntity> selectByTaskId(@Param("taskId") Long taskId);

    /**
     * 按 taskId 删除该任务下的所有入口行（discoverAndPersist 阶段 delete-then-insert 用）
     */
    @Delete("DELETE FROM ci_entrypoint WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Long taskId);
}