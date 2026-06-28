package com.company.codeinsight.modules.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验 db/data.sql（默认提示词种子）能在真实 PostgreSQL 上正确解析并落库。
 * <p>
 * 执行步骤：
 * <ol>
 *   <li>清空测试用 prompt 行（仅 prompt_type 属于本脚本的两种）</li>
 *   <li>读取 db/data.sql 并通过 ScriptUtils 拆句执行</li>
 *   <li>断言：MODULARIZE 与 DOCUMENT_GENERATION 各存在一条 is_default=1 的记录</li>
 * </ol>
 * 测试结束后会自动清理，避免污染其他测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class DataSqlSeedTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void seedFile_shouldInsertTwoDefaultPrompts() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // 1. 清理（仅清本次脚本会插入的两条记录，避免影响其它测试数据）
        jdbc.update("DELETE FROM ci_prompt WHERE name IN ('默认模块提取提示词', '默认文档生成提示词')");

        // 2. 读取并执行 data.sql
        ClassPathResource resource = new ClassPathResource("db/data.sql");
        String sql = new String(Files.readAllBytes(resource.getFile().toPath()), StandardCharsets.UTF_8);
        ScriptUtils.executeSqlScript(jdbc.getDataSource().getConnection(), resource);

        // 3. 断言两条默认记录都已插入
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT name, prompt_type, is_default, version FROM ci_prompt " +
                        "WHERE name IN ('默认模块提取提示词', '默认文档生成提示词') ORDER BY prompt_type");
        assertEquals(2, rows.size(), "应当插入两条默认提示词种子，实际数量=" + rows.size());

        boolean hasModularize = false;
        boolean hasDocument = false;
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("name");
            String promptType = (String) row.get("prompt_type");
            Number isDefault = (Number) row.get("is_default");
            Number version = (Number) row.get("version");
            assertEquals(1, isDefault.intValue(), name + " 应当 is_default=1");
            assertEquals(1, version.intValue(), name + " 应当 version=1");
            if ("MODULARIZE".equals(promptType)) {
                assertEquals("默认模块提取提示词", name);
                hasModularize = true;
            } else if ("DOCUMENT_GENERATION".equals(promptType)) {
                assertEquals("默认文档生成提示词", name);
                hasDocument = true;
            }
        }
        assertTrue(hasModularize, "缺少 MODULARIZE 默认提示词");
        assertTrue(hasDocument, "缺少 DOCUMENT_GENERATION 默认提示词");

        // 4. 二次执行应保持幂等
        ScriptUtils.executeSqlScript(jdbc.getDataSource().getConnection(), resource);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ci_prompt WHERE name IN ('默认模块提取提示词', '默认文档生成提示词')",
                Integer.class);
        assertEquals(2, count.intValue(), "二次执行后仍应只有 2 条记录");
    }
}