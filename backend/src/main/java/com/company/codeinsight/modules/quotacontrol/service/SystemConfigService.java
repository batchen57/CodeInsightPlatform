package com.company.codeinsight.modules.quotacontrol.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.quotacontrol.entity.SystemConfig;

import java.util.List;

public interface SystemConfigService extends IService<SystemConfig> {

    /**
     * 读取单个配置（文本）。未配置时返回 null。
     */
    String getString(String key);

    /**
     * 读取单个配置（int）。配置缺失或非数字时回退 defaultValue。
     */
    int getInt(String key, int defaultValue);

    /**
     * 读取单个配置（boolean）。配置缺失或非 true/false 时回退 defaultValue。
     */
    boolean getBoolean(String key, boolean defaultValue);

    /**
     * 写/更新单个配置。
     */
    void putString(String key, String value, String description, String updatedBy);

    /**
     * 列出所有配置（按 key 排序）。
     */
    List<SystemConfig> listAll();

    /**
     * 重新加载运行时缓存（写后调用）。
     */
    void refreshCache();
}
