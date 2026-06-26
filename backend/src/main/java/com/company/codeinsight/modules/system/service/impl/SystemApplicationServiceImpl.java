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

@Service
public class SystemApplicationServiceImpl extends ServiceImpl<SystemApplicationMapper, SystemApplication> implements SystemApplicationService {

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
