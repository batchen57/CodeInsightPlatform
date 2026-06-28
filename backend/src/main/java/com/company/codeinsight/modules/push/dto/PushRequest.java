package com.company.codeinsight.modules.push.dto;

import lombok.Data;

/**
 * 推送请求 DTO
 * 用于接收前端提交的推送任务参数。
 */
@Data
public class PushRequest {

    /** 要推送的知识版本 ID */
    private Long versionId;

    /** 推送方式，默认 "GIT" */
    private String pushMethod;
}
