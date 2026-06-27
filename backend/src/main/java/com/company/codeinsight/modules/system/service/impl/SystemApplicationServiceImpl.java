package com.company.codeinsight.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.system.entity.SystemApplication;
import com.company.codeinsight.modules.system.mapper.SystemApplicationMapper;
import com.company.codeinsight.modules.system.service.SystemApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 业务系统应用管理服务实现类
 * 负责系统配置的多条件模糊分页排序查询，以及系统启用与停用的状态管理。
 */
@Service
public class SystemApplicationServiceImpl extends ServiceImpl<SystemApplicationMapper, SystemApplication> implements SystemApplicationService {

    /**
     * 条件分页查询接入的业务系统列表，按创建时间倒序展示
     */
    @Override
    public Page<SystemApplication> listSystemsPage(int current, int size, String name, String owner, Integer status) {
        Page<SystemApplication> page = new Page<>(current, size);
        LambdaQueryWrapper<SystemApplication> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(name), SystemApplication::getName, name)
                .eq(StringUtils.hasText(owner), SystemApplication::getOwner, owner)
                .eq(status != null, SystemApplication::getStatus, status)
                .orderByDesc(SystemApplication::getCreatedAt);
        return this.page(page, queryWrapper);
    }

    /**
     * 变更系统应用状态，校验状态只能为 0 或 1
     */
    @Override
    public void changeStatus(Long id, Integer status) {
        SystemApplication system = this.getById(id);
        if (system == null) {
            throw new BusinessException("系统不存在");
        }
        if (status != 0 && status != 1) {
            throw new BusinessException("状态非法");
        }
        system.setStatus(status);
        this.updateById(system);
    }
}

