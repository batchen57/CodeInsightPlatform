package com.company.codeinsight.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 业务接入系统应用实体类
 * 对应数据库中的 ci_system 表，记录系统的基本信息、负责人以及启用停用状态。
 * 通过 deleted_at + MyBatis-Plus @TableLogic 实现软删除：所有查询自动过滤已删除记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_system")
public class SystemApplication extends BaseEntity {

    /**
     * 自增主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接入业务系统的名称（如 “电子商城系统”、“统一认证系统”）
     */
    private String name;

    /**
     * 业务系统的描述说明
     */
    private String description;

    /**
     * 系统的核心技术负责人/管理员用户名
     */
    private String owner;

    /**
     * 系统的启用状态：0-停用, 1-启用
     */
    private Integer status;

    /**
     * 逻辑删除时间。NULL=未删除，非空=已删除时间。
     * MyBatis-Plus 在执行查询时会自动追加 deleted_at IS NULL，过滤掉已删除记录。
     */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}

