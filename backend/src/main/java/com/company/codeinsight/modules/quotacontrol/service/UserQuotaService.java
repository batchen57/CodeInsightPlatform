package com.company.codeinsight.modules.quotacontrol.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.quotacontrol.entity.UserQuota;

public interface UserQuotaService extends IService<UserQuota> {

    /**
     * 按 userId 查找（包含 disabled=0 的记录），找不到返回 null。
     */
    UserQuota findByUserId(Long userId);

    /**
     * 分页查询（带可选 username 模糊过滤 + enabled 过滤）。
     */
    Page<UserQuota> pageQuery(int current, int size, String username, Integer enabled);
}
