package com.company.codeinsight.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.system.entity.SystemApplication;
import com.company.codeinsight.modules.system.vo.SystemSummaryVO;

/**
 * 业务系统应用管理服务接口
 * 负责定义接入系统的分页查询、状态机切换、软删除强校验等业务逻辑规则。
 */
public interface SystemApplicationService extends IService<SystemApplication> {

    /**
     * 分页多条件查询接入业务系统列表（带聚合指标：代码库数 / 知识版本数 / 最近扫描时间）
     * <p>优先按 state 过滤；旧 status 参数保留兼容（status=1 → ACTIVE）。</p>
     */
    Page<SystemSummaryVO> listSystemsPage(int current, int size, String name, String owner, Integer status, String state);

    /**
     * 变更指定接入系统应用的状态（1-启用, 0-停用）
     * @deprecated 请改用 {@link #changeState}
     */
    @Deprecated
    void changeStatus(Long id, Integer status);

    /**
     * 通过状态机切换系统状态。
     * 仅 ACTIVE / DISABLED 可被手动切换；其他状态由业务推进。
     */
    void changeState(Long id, String targetStateName);

    /**
     * 新建系统（向导 Step 1）：状态自动置 DRAFT，status 同步置 0。
     * 必填：name、owner。
     */
    SystemApplication createSystemDraft(SystemApplication system);

    /**
     * 软删除系统。强校验活跃任务，并级联软删除该系统下所有未删除的代码库。
     *
     * @param id 系统 ID
     * @throws com.company.codeinsight.common.exception.BusinessException 当存在未完成任务时
     */
    void softDeleteSystem(Long id);
}
