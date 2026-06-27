package com.company.codeinsight.modules.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.prompt.dto.PromptTestResultDto;
import com.company.codeinsight.modules.prompt.entity.DecompilePrompt;
import com.company.codeinsight.modules.prompt.mapper.DecompilePromptMapper;
import com.company.codeinsight.modules.prompt.service.DecompilePromptService;
import com.company.codeinsight.modules.model.mapper.AiModelMapper;
import com.company.codeinsight.modules.token.service.TokenAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 提示词模板管理服务实现类
 * 负责模板的查询、克隆备份、启动状态切换、大括号占位符渲染以及在线模型试跑测试（包含防网络抖动的 Mock 归纳生成器降级机制）。
 */
@Slf4j
@Service
public class DecompilePromptServiceImpl extends ServiceImpl<DecompilePromptMapper, DecompilePrompt> implements DecompilePromptService {

    @Value("${code-insight.ai.mock:true}")
    private boolean isMockAi;

    @Value("${code-insight.ai.api-key:}")
    private String apiKey;

    @Value("${code-insight.ai.api-url:https://api.minimax.io/v1}")
    private String apiUrl;

    @Value("${code-insight.ai.model-name:MiniMax-M3}")
    private String modelNameProp;

    @Autowired
    private AiModelMapper aiModelMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenAuditService tokenAuditService;

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    /**
     * 分页查询提示词模板，支持模糊搜索模板名称，按照创建时间倒序排列
     */
    @Override
    public Page<DecompilePrompt> listPromptsPage(int current, int size, String name, Integer status) {
        Page<DecompilePrompt> page = new Page<>(current, size);
        LambdaQueryWrapper<DecompilePrompt> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(name), DecompilePrompt::getName, name)
                .eq(status != null, DecompilePrompt::getStatus, status)
                .orderByDesc(DecompilePrompt::getCreatedAt);
        return this.page(page, queryWrapper);
    }

    /**
     * 复制/克隆提示词模板
     * 重新生成一份初始版本为 1、默认禁用的模板副本。
     */
    @Override
    public DecompilePrompt clonePrompt(Long id) {
        DecompilePrompt original = this.getById(id);
        if (original == null) {
            throw new BusinessException("提示词不存在");
        }
        DecompilePrompt cloned = new DecompilePrompt();
        cloned.setName(original.getName() + " - 副本");
        cloned.setContent(original.getContent());
        cloned.setVersion(1);
        cloned.setStatus(0); // 默认禁用
        cloned.setIsDefault(0); // 默认非默认
        this.save(cloned);
        return cloned;
    }

    /**
     * 修改提示词状态（0-禁用, 1-启用）
     * 并且在此提示词是默认模板时，排他性地修改其它模板为非默认。
     */
    @Override
    public void changeStatus(Long id, Integer status) {
        DecompilePrompt prompt = this.getById(id);
        if (prompt == null) {
            throw new BusinessException("提示词不存在");
        }
        if (status != 0 && status != 1) {
            throw new BusinessException("状态非法");
        }
        prompt.setStatus(status);
        this.updateById(prompt);

        // 如果被设为默认，并且状态是启用，将其他的设为非默认
        if (status == 1 && prompt.getIsDefault() == 1) {
            this.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DecompilePrompt>()
                    .ne(DecompilePrompt::getId, id)
                    .set(DecompilePrompt::getIsDefault, 0));
        }
    }

    /**
     * 对提示词模板中的 ${variable_name} 变量进行检索替换
     */
    @Override
    public String replaceVariables(String template, Map<String, String> variables) {
        if (!StringUtils.hasText(template) || variables == null) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String key = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(key, value);
        }
        return result;
    }

    /**
     * 在线测试运行提示词模板
     * 用一段示例代码和所选模型参数，调用大模型（或使用内置的高保真测试 MOCK 生成器降级流程）。
     */
    @Override
    public PromptTestResultDto testRun(Long id, String sampleCode, Long modelId) {
        DecompilePrompt prompt = this.getById(id);
        if (prompt == null) {
            throw new BusinessException("提示词模板不存在");
        }

        // 1. 动态从 Java 示例代码中正则匹配出类名和核心方法名
        String parsedClass = parseClassName(sampleCode);
        String parsedMethod = parseMethodName(sampleCode);

        // 2. 组装占位符映射
        Map<String, String> vars = new HashMap<>();
        vars.put("class_name", parsedClass);
        vars.put("method_name", parsedMethod);
        vars.put("source_code", sampleCode != null ? sampleCode : "public class MockTestClass { public void mockExecute() {} }");

        String filledPrompt = replaceVariables(prompt.getContent(), vars);

        // 3. 获取大模型配置
        com.company.codeinsight.modules.model.entity.AiModel model = null;
        if (modelId != null) {
            model = aiModelMapper.selectById(modelId);
        } else {
            // 获取系统设置的默认模型
            model = aiModelMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.codeinsight.modules.model.entity.AiModel>()
                            .eq(com.company.codeinsight.modules.model.entity.AiModel::getIsDefault, "true")
                            .last("LIMIT 1")
            );
        }

        String modelName = model != null ? model.getIdentifier() : this.modelNameProp;
        String activeApiKey = (model != null && StringUtils.hasText(model.getApiKey())) ? model.getApiKey() : this.apiKey;
        String activeApiUrl = (model != null && StringUtils.hasText(model.getBaseUrl())) ? model.getBaseUrl() : this.apiUrl;

        long start = System.currentTimeMillis();
        PromptTestResultDto result = new PromptTestResultDto();

        // 4. 判定是否满足使用 Mock 降级条件
        boolean shouldMock = this.isMockAi;
        if (!shouldMock) {
            if (!StringUtils.hasText(activeApiKey) || activeApiKey.startsWith("test-key") || "mock".equalsIgnoreCase(activeApiKey)) {
                shouldMock = true;
            }
        }

        if (shouldMock) {
            // 4.1 Mock 降级 - 使用高保真仿真分析器模拟文档生成
            result.setInputTokens(filledPrompt.length() / 4);
            String mockReply = generateMockTestResult(parsedClass, parsedMethod, sampleCode, modelName);
            result.setOutputTokens(mockReply.length() / 4);
            result.setDurationMs(System.currentTimeMillis() - start);
            result.setResult(mockReply);
            result.setErrorReason(null);

            // 记录审计日志
            tokenAuditService.logTokenUsage(null, null, modelName, result.getInputTokens(), result.getOutputTokens(), "TEST", true);
        } else {
            // 4.2 真实大模型调用，拼装标准 OpenAI 协议
            try {
                Map<String, Object> reqBody = new HashMap<>();
                reqBody.put("model", modelName);
                reqBody.put("stream", false);

                List<Map<String, String>> messages = new ArrayList<>();
                Map<String, String> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", filledPrompt);
                messages.add(userMsg);
                reqBody.put("messages", messages);

                String jsonPayload = objectMapper.writeValueAsString(reqBody);

                String requestUrl = activeApiUrl;
                if (!requestUrl.endsWith("/chat/completions")) {
                    requestUrl = requestUrl.replaceAll("/+$", "") + "/chat/completions";
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(requestUrl))
                        .header("Authorization", "Bearer " + activeApiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .timeout(Duration.ofSeconds(45))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long duration = System.currentTimeMillis() - start;

                if (response.statusCode() == 200) {
                    com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response.body());
                    String aiText = root.path("choices").get(0).path("message").path("content").asText();
                    int inTokens = root.path("usage").path("prompt_tokens").asInt();
                    int outTokens = root.path("usage").path("completion_tokens").asInt();

                    result.setInputTokens(inTokens);
                    result.setOutputTokens(outTokens);
                    result.setResult(aiText);
                    result.setDurationMs(duration);
                    result.setErrorReason(null);

                    tokenAuditService.logTokenUsage(null, null, modelName, inTokens, outTokens, "TEST", true);
                } else {
                    String errMsg = "HTTP 错误码: " + response.statusCode() + ", 详情: " + response.body();
                    result.setInputTokens(filledPrompt.length() / 4);
                    result.setOutputTokens(0);
                    result.setResult("真实模型调用失败: " + errMsg);
                    result.setDurationMs(duration);
                    result.setErrorReason(errMsg);

                    tokenAuditService.logTokenUsage(null, null, modelName, result.getInputTokens(), 0, "TEST", false);
                }
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - start;
                result.setInputTokens(filledPrompt.length() / 4);
                result.setOutputTokens(0);
                result.setResult("调用大模型网络异常: " + e.getMessage());
                result.setDurationMs(duration);
                result.setErrorReason(e.getMessage());

                tokenAuditService.logTokenUsage(null, null, modelName, result.getInputTokens(), 0, "TEST", false);
            }
        }

        return result;
    }

    /**
     * 正则提取测试代码中的类名
     */
    private String parseClassName(String sampleCode) {
        if (!StringUtils.hasText(sampleCode)) {
            return "MockTestClass";
        }
        Pattern pattern = Pattern.compile("(?:public\\s+)?(?:abstract\\s+|final\\s+)?(?:class|interface|enum)\\s+(\\w+)");
        Matcher matcher = pattern.matcher(sampleCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "MockTestClass";
    }

    /**
     * 正则提取测试代码中的核心方法名
     */
    private String parseMethodName(String sampleCode) {
        if (!StringUtils.hasText(sampleCode)) {
            return "mockExecute";
        }
        Pattern pattern = Pattern.compile("(?:public|protected|private|static|final|synchronized|\\s)+\\s+[\\w<>\\[\\],.?\\s]+\\s+(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(sampleCode);
        while (matcher.find()) {
            String mName = matcher.group(1).trim();
            if (!List.of("if", "for", "while", "switch", "class", "synchronized", "catch").contains(mName)) {
                return mName;
            }
        }
        return "mockExecute";
    }

    /**
     * 高逼真仿真测试结果生成器
     */
    private String generateMockTestResult(String className, String methodName, String sampleCode, String modelName) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("## 1. 职责概述\n");
        if (className.endsWith("Controller")) {
            sb.append("`").append(className).append("` 是一个接口访问控制层控制器。定义了针对相关资源的 REST 路由接口，负责前端请求参数校验、数据分发和统一响应格式的封装输出。\n\n");
        } else if (className.endsWith("Service")) {
            sb.append("`").append(className).append("` 是系统的核心业务逻辑服务类。负责处理业务决策、状态机转换和数据处理的事务边界。协调各个数据操作的 Mapper 接口以提供完整的业务支持。\n\n");
        } else if (className.endsWith("Mapper")) {
            sb.append("`").append(className).append("` 是 MyBatis 数据访问持久层接口。通过注解或 XML 配置底层的 SQL 语句，实现与数据库表的映射，为业务层提供原子级的数据查询与存取支持。\n\n");
        } else {
            sb.append("`").append(className).append("` 是一个业务处理类，定义了核心业务流的骨架，负责组织 and 执行对应的数据处理逻辑。\n\n");
        }
        
        sb.append("## 2. 核心流程\n");
        if (StringUtils.hasText(methodName) && !"mockExecute".equals(methodName)) {
            sb.append("该类的核心业务方法为 `").append(methodName).append("`。主要执行以下步骤：\n");
            if (methodName.startsWith("get") || methodName.startsWith("select") || methodName.startsWith("list")) {
                sb.append("1. 接收查询条件及分页/排序参数。\n");
                sb.append("2. 校验参数有效性，若不合法则抛出业务异常。\n");
                sb.append("3. 调用底层的持久层接口，执行只读 SQL 查询，获取匹配的实体记录。\n");
                sb.append("4. 将实体记录转换为 DTO/VO 传输对象并返回。\n\n");
            } else if (methodName.startsWith("save") || methodName.startsWith("create") || methodName.startsWith("insert")) {
                sb.append("1. 接收需要保存的业务实体数据。\n");
                sb.append("2. 进行前置业务校验（如重复性校验、字段长度校验等）。\n");
                sb.append("3. 开启本地事务，调用持久层向数据库插入/更新记录。\n");
                sb.append("4. 记录操作流水日志，在成功后返回对应的主键 ID。\n\n");
            } else {
                sb.append("1. 接收输入参数，加载所需的业务上下文。\n");
                sb.append("2. 执行核心状态判断和业务逻辑计算。\n");
                sb.append("3. 协调相关组件/持久层，更新数据库状态。\n");
                sb.append("4. 记录审计日志，返回执行状态或处理结果。\n\n");
            }
        } else {
            sb.append("1. 解析输入数据，提取关键字段。\n");
            sb.append("2. 调用依赖的业务组件，按序处理核心节点流程。\n");
            sb.append("3. 保存状态变更，记录操作日志。\n\n");
        }
        
        sb.append("## 3. 重要依赖\n");
        List<String> deps = new ArrayList<>();
        Pattern depPattern = Pattern.compile("(?:private|protected|public)\\s+(?:final\\s+)?([A-Z][\\w]*(?:<[^>]+>)?)\\s+(\\w+)\\s*(?:=|;)");
        Matcher depMatcher = depPattern.matcher(sampleCode != null ? sampleCode : "");
        while (depMatcher.find()) {
            String depType = depMatcher.group(1);
            String depName = depMatcher.group(2);
            deps.add("`" + depName + "` (" + depType + ")");
        }
        if (!deps.isEmpty()) {
            for (String dep : deps) {
                sb.append("- 依赖组件: ").append(dep).append("\n");
            }
            sb.append("\n");
        } else {
            sb.append("- 暂无明显外部依赖（或仅依赖基础类库）。\n\n");
        }
        
        sb.append("## 4. 待复核事项\n");
        sb.append("- [ ] 该类在处理高并发场景时的事务隔离级别是否符合业务预期？\n");
        sb.append("- [ ] 异常情况下的熔断/降级策略是否完整配置并在前端呈现？\n\n");

        sb.append("> *(注：当前试跑使用的是仿真 Mock 生成器，模拟模型：").append(modelName).append(")*");
        
        return sb.toString();
    }
}
