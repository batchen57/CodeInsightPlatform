package com.company.codeinsight.modules.repository.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.repository.entity.CodeRepository;

/**
 * 代码仓库管理服务接口
 * 负责定义代码仓库列表分页查询、Git 网络测试连通性、软删除强校验等业务规则。
 */
public interface CodeRepositoryService extends IService<CodeRepository> {

    /**
     * 分页多条件查询代码仓库配置记录列表
     */
    Page<CodeRepository> listRepositoriesPage(int current, int size, Long systemId, String gitUrl);

    /**
     * 对已保存的仓库记录进行连接有效性测试
     */
    boolean testConnection(Long id);

    /**
     * 针对新输入的仓库参数进行实时连接有效性测试
     */
    boolean testConnection(String gitUrl, String branch, String username, String password);

    /**
     * 软删除代码库。强校验：存在活跃任务时拒绝。
     *
     * @param id 仓库 ID
     */
    void softDeleteRepository(Long id);
}
