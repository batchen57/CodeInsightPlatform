package com.company.codeinsight.modules.repository.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.repository.entity.CodeRepository;

public interface CodeRepositoryService extends IService<CodeRepository> {
    Page<CodeRepository> listRepositoriesPage(int current, int size, Long systemId, String gitUrl);
    boolean testConnection(Long id);
    boolean testConnection(String gitUrl, String branch, String username, String password);
}
