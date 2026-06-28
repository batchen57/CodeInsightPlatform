package com.company.codeinsight.modules.push.strategy;

import org.springframework.stereotype.Component;

import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.knowledge.entity.KnowledgeVersion;
import com.company.codeinsight.modules.push.entity.PushTask;
import com.company.codeinsight.modules.push.enums.PushMethod;

import lombok.extern.slf4j.Slf4j;

/**
 * S3 对象存储推送策略（接口预留）
 *
 * 未来实现：压缩知识文档 → 上传到 S3 兼容存储桶 → 替换云端内容。
 * 当前为占位实现，调用时抛出友好提示。
 */
@Slf4j
@Component("s3PushStrategy")
public class S3PushStrategy implements PushStrategy {

    @Override
    public String execute(KnowledgeVersion version, PushTask task) {
        log.warn("S3 push is not yet implemented for version {}", version.getId());
        throw new BusinessException("S3 push is not yet implemented");
    }

    @Override
    public PushMethod getMethod() {
        return PushMethod.S3;
    }
}
