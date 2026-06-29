package com.company.codeinsight.modules.quotacontrol.dto;

import lombok.Data;

@Data
public class UserQuotaRequest {
    private Long userId;
    private Integer dailyTokenLimit;
    private Integer monthlyTokenLimit;
    private Integer enabled;
    private String remark;
}
