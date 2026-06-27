package com.company.codeinsight.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.system.entity.SystemApplication;

/**
 * 业务系统应用管理服务接口
 * 负责定义接入系统的分页查询、启用停用切换等业务逻辑规则。
 */
public interface SystemApplicationService extends IService<SystemApplication> {

    /**
     * 分页多条件查询接入业务系统列表
     */
    Page<SystemApplication> listSystemsPage(int current, int size, String name, String owner, Integer status);

    /**
     * 变更指定接入系统应用的状态（1-启用, 0-停用）
     *
     * @param id     系统 ID
     * @param status 目标状态
     */
    void changeStatus(Long id, Integer status);
}

