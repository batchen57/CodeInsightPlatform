package com.company.codeinsight.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.repository.entity.CodeRepository;
import com.company.codeinsight.modules.repository.mapper.CodeRepositoryMapper;
import com.company.codeinsight.modules.system.entity.SystemApplication;
import com.company.codeinsight.modules.system.enums.SystemState;
import com.company.codeinsight.modules.system.mapper.SystemApplicationMapper;
import com.company.codeinsight.modules.system.service.SystemApplicationService;
import com.company.codeinsight.modules.system.service.SystemStateMachineService;
import com.company.codeinsight.modules.system.vo.SystemSummaryVO;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.mapper.DecompileTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 业务系统应用管理服务实现类
 * 负责系统配置的多条件模糊分页排序查询、状态机维护、聚合指标查询以及软删除前的强校验。
 */
@Service
public class SystemApplicationServiceImpl extends ServiceImpl<SystemApplicationMapper, SystemApplication> implements SystemApplicationService {

    /** 处于活跃态的任务集合，这些状态下不允许删除关联系统/仓库 */
    private static final Set<String> ACTIVE_TASK_STATUSES = Set.of(
            "PENDING", "PULLING_CODE", "PARSING_CODE", "SPLITTING_TASK",
            "AI_ANALYZING", "GENERATING_DOC", "REVIEWING", "PUSHING"
    );

    @Autowired
    private CodeRepositoryMapper codeRepositoryMapper;

    @Autowired
    private DecompileTaskMapper decompileTaskMapper;

    @Autowired
    private SystemStateMachineService stateMachineService;

    /**
     * 条件分页查询接入的业务系统列表（带聚合指标）
     * <p>state 优先；旧 status 参数（0/1）作为兼容输入。</p>
     */
    @Override
    public Page<SystemSummaryVO> listSystemsPage(int current, int size, String name, String owner, Integer status, String state) {
        // 优先使用新 state 过滤；否则按旧 status 转换（status=1 → ACTIVE, status=0 → 其他未启用态）
        String effectiveState = state;
        if (!StringUtils.hasText(effectiveState) && status != null) {
            // 旧 status=0 表示「未启用」，含 DRAFT/REPO/SCAN/PROMPT/DISABLED；status=1 仅 ACTIVE
            // 在 mapper 层用 IN 过滤；这里把单值映射成单值：status=1 → ACTIVE；status=0 → 不传（视为全部）
            if (status == 1) {
                effectiveState = SystemState.ACTIVE.name();
            } else {
                effectiveState = null;
            }
        }
        List<SystemSummaryVO> all = baseMapper.listSystemsWithSummary(name, owner, effectiveState);
        long total = all.size();
        int from = Math.max(0, (current - 1) * size);
        int to = Math.min(all.size(), from + size);
        List<SystemSummaryVO> pageData = all.subList(from, to);
        Page<SystemSummaryVO> page = new Page<>(current, size, total);
        page.setRecords(pageData);
        return page;
    }

    /**
     * 变更系统应用状态（0/1 二态兼容）。
     * 0 → DISABLED（任何非 ACTIVE 都可）；1 → ACTIVE（仅 PROMPT_CONFIGURED 可启）
     * @deprecated 请改用 {@link #changeState}
     */
    @Override
    @Deprecated
    public void changeStatus(Long id, Integer status) {
        if (id == null || status == null) {
            throw new BusinessException("id/status 不能为空");
        }
        if (status == 1) {
            changeState(id, SystemState.ACTIVE.name());
        } else if (status == 0) {
            changeState(id, SystemState.DISABLED.name());
        } else {
            throw new BusinessException("状态非法");
        }
    }

    /**
     * 状态机切换。传入字符串目标态（非合法值抛错）。
     */
    @Override
    public void changeState(Long id, String targetStateName) {
        if (id == null) {
            throw new BusinessException("系统 id 不能为空");
        }
        SystemState target = SystemState.parse(targetStateName);
        if (target != SystemState.ACTIVE && target != SystemState.DISABLED) {
            throw new BusinessException("仅支持手动切换 ACTIVE / DISABLED");
        }
        stateMachineService.transitTo(id, target);
    }

    /**
     * 新建系统：状态写 DRAFT（旧 status 字段保持 null，向后兼容）。
     */
    public SystemApplication createSystemDraft(SystemApplication system) {
        if (system == null) {
            throw new BusinessException("系统数据不能为空");
        }
        if (!StringUtils.hasText(system.getName())) {
            throw new BusinessException("系统名称(name)必填");
        }
        if (!StringUtils.hasText(system.getOwner())) {
            throw new BusinessException("负责人(owner)必填");
        }
        system.setId(null);
        system.setState(SystemState.DRAFT.name());
        // state=DRAFT 不是 ACTIVE，所以 status 同步为 0（仅作兼容）
        system.setStatus(0);
        this.save(system);
        return system;
    }

    /**
     * 软删除系统。
     * <p>强校验：</p>
     * <ol>
     *     <li>系统下存在处于活跃态的任务（PENDING/扫描/AI/推送等）时拒绝删除</li>
     *     <li>同时级联软删除该系统下所有未删除的代码库</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteSystem(Long id) {
        SystemApplication system = this.getById(id);
        if (system == null) {
            throw new BusinessException("系统不存在");
        }

        // 1. 校验：是否有活跃态任务
        long activeTaskCount = countActiveTasksBySystemId(id);
        if (activeTaskCount > 0) {
            throw new BusinessException("该系统下存在 " + activeTaskCount + " 个未完成任务，请先终止或等待完成后再删除");
        }

        // 2. 校验：下挂代码库是否也有活跃任务
        List<CodeRepository> repos = codeRepositoryMapper.selectList(
                new LambdaQueryWrapper<CodeRepository>()
                        .eq(CodeRepository::getSystemId, id)
                        .isNull(CodeRepository::getDeletedAt)
        );
        for (CodeRepository repo : repos) {
            if (countActiveTasksByRepoId(repo.getId()) > 0) {
                throw new BusinessException("代码库【" + repo.getGitUrl() + "】下存在未完成任务，请先处理");
            }
        }

        // 3. 级联软删除代码库
        if (!repos.isEmpty()) {
            for (CodeRepository repo : repos) {
                repo.setDeletedAt(java.time.LocalDateTime.now());
            }
            for (CodeRepository repo : repos) {
                codeRepositoryMapper.updateById(repo);
            }
        }

        // 4. 软删除系统本体
        system.setDeletedAt(java.time.LocalDateTime.now());
        this.updateById(system);
    }

    private long countActiveTasksBySystemId(Long systemId) {
        Long count = decompileTaskMapper.selectCount(
                new LambdaQueryWrapper<DecompileTask>()
                        .eq(DecompileTask::getSystemId, systemId)
                        .in(DecompileTask::getStatus, ACTIVE_TASK_STATUSES)
        );
        return count == null ? 0 : count;
    }

    private long countActiveTasksByRepoId(Long repoId) {
        Long count = decompileTaskMapper.selectCount(
                new LambdaQueryWrapper<DecompileTask>()
                        .eq(DecompileTask::getRepositoryId, repoId)
                        .in(DecompileTask::getStatus, ACTIVE_TASK_STATUSES)
        );
        return count == null ? 0 : count;
    }
}
