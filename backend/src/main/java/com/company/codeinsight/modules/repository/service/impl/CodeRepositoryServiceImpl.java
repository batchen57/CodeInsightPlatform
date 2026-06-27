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

/**
 * 代码仓库管理服务实现类
 * 负责组装条件模糊分页查询代码库，并使用 JGit 工具的 `LsRemoteCommand` 实时进行远程 Git 仓库的连接、证书合法性及网络连通性校验。
 */
@Slf4j
@Service
public class CodeRepositoryServiceImpl extends ServiceImpl<CodeRepositoryMapper, CodeRepository> implements CodeRepositoryService {

    /**
     * 分页模糊匹配获取 Git 仓库配置，按创建时间倒序展示
     */
    @Override
    public Page<CodeRepository> listRepositoriesPage(int current, int size, Long systemId, String gitUrl) {
        Page<CodeRepository> page = new Page<>(current, size);
        LambdaQueryWrapper<CodeRepository> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(systemId != null, CodeRepository::getSystemId, systemId)
                .like(StringUtils.hasText(gitUrl), CodeRepository::getGitUrl, gitUrl)
                .orderByDesc(CodeRepository::getCreatedAt);
        return this.page(page, queryWrapper);
    }

    /**
     * 针对已保存的仓库，读取密码明文后进行连通性校验
     */
    @Override
    public boolean testConnection(Long id) {
        CodeRepository repo = this.getById(id);
        if (repo == null) {
            throw new BusinessException("代码库配置不存在");
        }
        return testConnection(repo.getGitUrl(), repo.getBranch(), repo.getUsername(), repo.getPassword());
    }

    /**
     * 核心 JGit 连通性测试方法
     * 发送轻量级 ls-remote 请求至 Git 服务器校验连通性，避免直接 clone 整个大仓库带来过高的网络吞吐消耗。
     */
    @Override
    public boolean testConnection(String gitUrl, String branch, String username, String password) {
        if (!StringUtils.hasText(gitUrl)) {
            return false;
        }
        try {
            // LsRemoteCommand 发起轻量引用探测以验证可达性
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

