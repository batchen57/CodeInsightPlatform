package com.company.codeinsight.modules.scanner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ci_file_snapshot")
public class CodeFileSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String filePath;

    private String fileType;

    private Integer lineCount;

    private String fileHash;

    private String contentUri;

    private LocalDateTime createdAt;
}
