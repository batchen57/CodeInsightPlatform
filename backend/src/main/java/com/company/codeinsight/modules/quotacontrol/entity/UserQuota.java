package com.company.codeinsight.modules.quotacontrol.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户额度。对应 ci_user_quota。
 * 0 表示不限。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_user_quota")
public class UserQuota extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Integer dailyTokenLimit;
    private Integer monthlyTokenLimit;
    private Integer enabled;
    private String remark;
}
