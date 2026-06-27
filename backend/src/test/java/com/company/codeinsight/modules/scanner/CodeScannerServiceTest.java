package com.company.codeinsight.modules.scanner;

import com.company.codeinsight.modules.repository.entity.CodeRepository;
import com.company.codeinsight.modules.repository.service.CodeRepositoryService;
import com.company.codeinsight.modules.scanner.entity.CodeFileSnapshot;
import com.company.codeinsight.modules.scanner.service.CodeScannerService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@ActiveProfiles("test")
@Transactional
public class CodeScannerServiceTest {

    @Autowired
    private CodeScannerService codeScannerService;

    @Autowired
    private CodeRepositoryService repositoryService;

    @Test
    public void testPullAndScanFallback() {
        // 创建一个测试用的代码库配置
        CodeRepository repo = new CodeRepository();
        repo.setSystemId(1L);
        repo.setGitUrl("https://github.com/invalid-url-to-trigger-fallback/repo.git");
        repo.setBranch("master");
        repo.setExcludeDirs(".git,target");
        repo.setExcludeFileTypes("class,jar");
        repositoryService.save(repo);

        // 执行扫描
        File dir = codeScannerService.pullAndScan(999L, repo.getId());
        Assertions.assertNotNull(dir);
        Assertions.assertTrue(dir.exists());

        // 验证快照是否写入数据库
        List<CodeFileSnapshot> snapshots = codeScannerService.getSnapshotsByTaskId(999L);
        Assertions.assertFalse(snapshots.isEmpty());

        // 验证是否有 Controller 且没有 class 文件
        boolean hasController = false;
        for (CodeFileSnapshot snapshot : snapshots) {
            Assertions.assertFalse(snapshot.getFilePath().contains(".class"));
            if (snapshot.getFilePath().contains("UserController.java")) {
                hasController = true;
            }
        }
        Assertions.assertTrue(hasController);
    }
}
