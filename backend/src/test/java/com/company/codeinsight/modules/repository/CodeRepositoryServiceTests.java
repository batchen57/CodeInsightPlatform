package com.company.codeinsight.modules.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.codeinsight.modules.repository.entity.CodeRepository;
import com.company.codeinsight.modules.repository.service.CodeRepositoryService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@ActiveProfiles("test")
@Transactional
public class CodeRepositoryServiceTests {

    @Autowired
    private CodeRepositoryService codeRepositoryService;

    @Test
    public void testCrud() {
        CodeRepository repo = new CodeRepository();
        repo.setSystemId(1L);
        repo.setGitUrl("https://github.com/dummy/repo.git");
        repo.setBranch("main");
        repo.setUsername("user");
        repo.setPassword("pass");
        repo.setScanRoot("/");

        // Save
        boolean saved = codeRepositoryService.save(repo);
        Assertions.assertTrue(saved);
        Assertions.assertNotNull(repo.getId());

        // Get
        CodeRepository fetched = codeRepositoryService.getById(repo.getId());
        Assertions.assertEquals("main", fetched.getBranch());
        Assertions.assertEquals("https://github.com/dummy/repo.git", fetched.getGitUrl());

        // Page list
        Page<CodeRepository> page = codeRepositoryService.listRepositoriesPage(1, 10, 1L, "dummy");
        Assertions.assertTrue(page.getTotal() > 0);

        // Update
        fetched.setBranch("develop");
        codeRepositoryService.updateById(fetched);
        CodeRepository updated = codeRepositoryService.getById(repo.getId());
        Assertions.assertEquals("develop", updated.getBranch());

        // Delete
        boolean removed = codeRepositoryService.removeById(repo.getId());
        Assertions.assertTrue(removed);
    }

    @Test
    public void testGitConnectionFail() {
        boolean connected = codeRepositoryService.testConnection("https://invalid-git-url-xyz.com/repo.git", "main", "user", "pass");
        Assertions.assertFalse(connected);
    }
}
