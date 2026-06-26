package com.company.codeinsight.modules.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ci_operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long systemId;

    private Long taskId;

    private Long userId;

    private String username;

    private String actionType;

    private String detail;

    private String ipAddress;

    private String exceptionMsg;

    private Integer isSuccess; // 0-失败, 1-成功

    private LocalDateTime createdAt;
}
