package com.company.codeinsight.modules.repository.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.common.response.PageResult;
import com.company.codeinsight.modules.log.service.OperationLogService;
import com.company.codeinsight.modules.repository.entity.CodeRepository;
import com.company.codeinsight.modules.repository.service.CodeRepositoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "代码库管理", description = "代码库配置及连接测试接口")
@RestController
@RequestMapping("/repositories")
@Validated
public class CodeRepositoryController {

    @Autowired
    private CodeRepositoryService codeRepositoryService;

    @Autowired
    private OperationLogService operationLogService;

    @Operation(summary = "新增代码库")
    @PostMapping
    public ApiResponse<CodeRepository> createRepository(@Valid @RequestBody CodeRepository repository) {
        repository.setId(null);
        codeRepositoryService.save(repository);
        operationLogService.logOperation(repository.getSystemId(), null, "CREATE_REPO", "创建代码库: " + repository.getGitUrl(), null, true);
        maskPassword(repository);
        return ApiResponse.success(repository);
    }

    @Operation(summary = "编辑代码库")
    @PutMapping("/{id}")
    public ApiResponse<CodeRepository> updateRepository(@PathVariable Long id, @Valid @RequestBody CodeRepository repository) {
        repository.setId(id);
        if ("******".equals(repository.getPassword())) {
            CodeRepository existing = codeRepositoryService.getById(id);
            if (existing != null) {
                repository.setPassword(existing.getPassword());
            }
        }
        codeRepositoryService.updateById(repository);
        operationLogService.logOperation(repository.getSystemId(), null, "UPDATE_REPO", "更新代码库: " + repository.getGitUrl(), null, true);
        maskPassword(repository);
        return ApiResponse.success(repository);
    }

    @Operation(summary = "代码库详情")
    @GetMapping("/{id}")
    public ApiResponse<CodeRepository> getRepository(@PathVariable Long id) {
        CodeRepository repository = codeRepositoryService.getById(id);
        maskPassword(repository);
        return ApiResponse.success(repository);
    }

    @Operation(summary = "代码库分页查询")
    @GetMapping
    public ApiResponse<PageResult<CodeRepository>> listRepositories(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long systemId,
            @RequestParam(required = false) String gitUrl) {
        Page<CodeRepository> page = codeRepositoryService.listRepositoriesPage(current, size, systemId, gitUrl);
        page.getRecords().forEach(this::maskPassword);
        PageResult<CodeRepository> result = new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(), page.getRecords());
        return ApiResponse.success(result);
    }

    @Operation(summary = "测试 Git 连接 (未保存)")
    @PostMapping("/test-connection")
    public ApiResponse<Boolean> testConnectionBeforeSave(@RequestBody CodeRepository repository) {
        boolean connected = codeRepositoryService.testConnection(
                repository.getGitUrl(),
                repository.getBranch(),
                repository.getUsername(),
                "******".equals(repository.getPassword()) && repository.getId() != null ?
                        codeRepositoryService.getById(repository.getId()).getPassword() : repository.getPassword()
        );
        return ApiResponse.success(connected);
    }

    @Operation(summary = "测试 Git 连接 (已保存)")
    @PostMapping("/{id}/test-connection")
    public ApiResponse<Boolean> testConnectionSaved(@PathVariable Long id) {
        boolean connected = codeRepositoryService.testConnection(id);
        return ApiResponse.success(connected);
    }

    private void maskPassword(CodeRepository repo) {
        if (repo != null && StringUtils.hasText(repo.getPassword())) {
            repo.setPassword("******");
        }
    }
}
