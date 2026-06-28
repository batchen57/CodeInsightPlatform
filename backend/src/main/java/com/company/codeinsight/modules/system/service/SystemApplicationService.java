package com.company.codeinsight.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.system.entity.SystemApplication;
import com.company.codeinsight.modules.system.vo.SystemSummaryVO;

/**
 * 业务系统应用管理服务接口
 * 负责定义接入系统的分页查询、启用停用切换、软删除强校验等业务逻辑规则。
 */
public interface SystemApplicationService extends IService<SystemApplication> {

    /**
     * 分页多条件查询接入业务系统列表（带聚合指标：代码库数 / 知识版本数 / 最近扫描时间）
     */
    Page<SystemSummaryVO> listSystemsPage(int current, int size, String name, String owner, Integer status);

    /**
     * 变更指定接入系统应用的状态（1-启用, 0-停用）
     */
    void changeStatus(Long id, Integer status);

    /**
     * 软删除系统。强校验活跃任务，并级联软删除该系统下所有未删除的代码库。
     *
     * @param id 系统 ID
     * @throws BusinessException 当存在未完成任务时
     */
    void softDeleteSystem(Long id);
}
