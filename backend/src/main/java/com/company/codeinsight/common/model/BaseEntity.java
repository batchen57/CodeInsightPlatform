package com.company.codeinsight.common.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据库实体类基类
 * 抽离并封装关系型数据表中通用的审计跟踪属性（如创建时间、更新时间）。
 * 子类实体继承此类后，可通过 MyBatis-Plus 自动填充功能实现零手动干预的记录时间戳更新。
 */
@Data
public class BaseEntity {

    // 创建时间：在执行数据库新增 insert 动作时自动注入填充，不可手动更新
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // 更新时间：在执行新增或数据库 update 动作时由拦截器自动修改填充
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

