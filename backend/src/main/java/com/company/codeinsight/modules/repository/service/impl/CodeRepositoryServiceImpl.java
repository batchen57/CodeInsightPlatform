package com.company.codeinsight.modules.repository.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.repository.entity.CodeRepository;
import com.company.codeinsight.modules.repository.mapper.CodeRepositoryMapper;
import com.company.codeinsight.modules.repository.service.CodeRepositoryService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;

@Slf4j
@Service
public class CodeRepositoryServiceImpl extends ServiceImpl<CodeRepositoryMapper, CodeRepository> implements CodeRepositoryService {

    @Override
    public Page<CodeRepository> listRepositoriesPage(int current, int size, Long systemId, String gitUrl) {
        Page<CodeRepository> page = new Page<>(current, size);
        LambdaQueryWrapper<CodeRepository> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(systemId != null, CodeRepository::getSystemId, systemId)
                .like(StringUtils.hasText(gitUrl), CodeRepository::getGitUrl, gitUrl)
                .orderByDesc(CodeRepository::getCreatedAt);
        return this.page(page, queryWrapper);
    }

    @Override
    public boolean testConnection(Long id) {
        CodeRepository repo = this.getById(id);
        if (repo == null) {
            throw new BusinessException("代码库配置不存在");
        }
        return testConnection(repo.getGitUrl(), repo.getBranch(), repo.getUsername(), repo.getPassword());
    }

    @Override
    public boolean testConnection(String gitUrl, String branch, String username, String password) {
        if (!StringUtils.hasText(gitUrl)) {
            return false;
        }
        try {
            LsRemoteCommand lsRemote = Git.lsRemoteRepository().setRemote(gitUrl);
            if (StringUtils.hasText(username)) {
                lsRemote.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password != null ? password : ""));
            }
            Collection<Ref> refs = lsRemote.call();
            return refs != null && !refs.isEmpty();
        } catch (Exception e) {
            log.error("JGit test connection failed for remote: " + gitUrl, e);
            return false;
        }
    }
}
