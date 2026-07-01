package com.company.codeinsight.modules.scanwindow.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
public class ScanWindowDto {
    @NotNull
    private Long repositoryId;
    @NotNull
    @Min(0) @Max(127)
    private Integer weekDays;
    @NotNull
    @Min(0) @Max(23)
    private Integer hour;
    @NotNull
    @Min(0) @Max(59)
    private Integer minute;
    @NotNull
    private Boolean enabled;
}
