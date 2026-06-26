package com.company.codeinsight.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.system.entity.SystemApplication;

public interface SystemApplicationService extends IService<SystemApplication> {
    Page<SystemApplication> listSystemsPage(int current, int size, String name, String owner, Integer status);
    void changeStatus(Long id, Integer status);
}
