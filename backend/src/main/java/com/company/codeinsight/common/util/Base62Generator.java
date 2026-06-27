package com.company.codeinsight.common.util;

import com.company.codeinsight.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Set;

/**
 * 5 位 Base62 ID 生成器
 * 字符集：0-9a-zA-Z，5 位共计 916,132,832 种组合，冲突概率极低。
 * 与业务约定：模块 ID 以 m 前缀（如 m0B1A2）、子模块 s 前缀、功能 f 前缀。
 */
@Component
public class Base62Generator {

    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int DEFAULT_LEN = 5;
    private static final int MAX_RETRY = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 生成 5 位 Base62 字符串（不含前缀） */
    public String generate() {
        return generate(DEFAULT_LEN);
    }

    public String generate(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return sb.toString();
    }

    /** 生成带前缀的 ID（如 "m" + 5位 Base62 = mXXXXX） */
    public String generateWithPrefix(char prefix) {
        return prefix + generate();
    }

    /**
     * 在已有 ID 集合中生成一个不冲突的新 ID（重试 MAX_RETRY 次）
     * @param prefix  层级前缀（'m' / 's' / 'f'）
     * @param existing 已存在的 ID 集合（不含前缀或含前缀均可，内部按完整 ID 校验）
     */
    public String generateUnique(char prefix, Set<String> existing) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            String candidate = generateWithPrefix(prefix);
            if (existing == null || !existing.contains(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException("Base62 ID 冲突检测失败（已重试 " + MAX_RETRY + " 次）");
    }
}