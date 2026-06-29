package com.company.codeinsight.modules.quotacontrol.dto;

import lombok.Data;

@Data
public class SystemConfigUpdateRequest {
    private String value;
    private String description;
}
