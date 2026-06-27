package com.company.codeinsight.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.ai.entity.AiCallRecord;
import com.company.codeinsight.modules.ai.mapper.AiCallRecordMapper;
import com.company.codeinsight.modules.callchain.entity.MethodCall;
import com.company.codeinsight.modules.callchain.mapper.MethodCallMapper;
import com.company.codeinsight.modules.ai.service.AiSummaryService;
import com.company.codeinsight.modules.chunk.entity.CodeChunk;
import com.company.codeinsight.modules.chunk.mapper.CodeChunkMapper;
import com.company.codeinsight.modules.draft.entity.DraftWorkspace;
import com.company.codeinsight.modules.draft.entity.KnowledgeDraft;
import com.company.codeinsight.modules.draft.mapper.DraftWorkspaceMapper;
import com.company.codeinsight.modules.draft.mapper.KnowledgeDraftMapper;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.mapper.DecompileTaskMapper;
import com.company.codeinsight.modules.token.service.TokenAuditService;
import com.company.codeinsight.modules.parser.service.JavaParserService;
import com.company.codeinsight.modules.parser.model.ParsedClassInfo;
import com.company.codeinsight.modules.draft.entity.DraftSourceReference;
import com.company.codeinsight.modules.draft.mapper.DraftSourceReferenceMapper;
import com.company.codeinsight.modules.repository.entity.CodeRepository;
import com.company.codeinsight.modules.repository.mapper.CodeRepositoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class AiSummaryServiceImpl implements AiSummaryService {

    // 数据分片持久层映射
    @Autowired
    private CodeChunkMapper chunkMapper;

    // AI 调用历史记录映射
    @Autowired
    private AiCallRecordMapper aiCallRecordMapper;

    // Token 消耗审计与流控服务
    @Autowired
    private TokenAuditService tokenAuditService;

    // 草稿工作区映射
    @Autowired
    private DraftWorkspaceMapper draftWorkspaceMapper;

    // 知识草稿内容映射
    @Autowired
    private KnowledgeDraftMapper knowledgeDraftMapper;

    // 任务实体数据映射
    @Autowired
    private DecompileTaskMapper decompileTaskMapper;

    // Java 静态解析服务组件
    @Autowired
    private JavaParserService javaParserService;

    // 代码来源引用映射
    @Autowired
    private DraftSourceReferenceMapper draftSourceReferenceMapper;

    @Autowired
    private CodeRepositoryMapper repositoryMapper;

    @Autowired
    private com.company.codeinsight.modules.model.mapper.AiModelMapper aiModelMapper;

    @Autowired
    private com.company.codeinsight.modules.prompt.mapper.DecompilePromptMapper promptMapper;

    @Autowired
    private com.company.codeinsight.modules.hierarchy.service.ModuleHierarchyService moduleHierarchyService;

    @Autowired
    private com.company.codeinsight.modules.entrypoint.service.EntryPointDiscoveryService entryPointDiscoveryService;

    @Autowired
    private com.company.codeinsight.common.util.PromptTemplateLoader promptTemplateLoader;

    @Autowired
    private MethodCallMapper methodCallMapper;

    // 是否启用 AI 本地 Mock 仿真
    @Value("${code-insight.ai.mock:false}")
    private boolean aiMock;

    // 大模型服务访问密钥
    @Value("${code-insight.ai.api-key:}")
    private String apiKey;

    // 大模型服务接口基础地址 (默认为 MiniMax 服务端点)
    @Value("${code-insight.ai.api-url:https://api.minimax.io/v1}")
    private String apiUrl;

    // 默认选用的大模型版本名称
    @Value("${code-insight.ai.model-name:MiniMax-M3}")
    private String modelName;

    // 本地磁盘存储路径 (草稿物理正文暂存区)
    @Value("${code-insight.storage.local-path:./storage}")
    private String localStoragePath;

    // JSON 数据映射工具
    private final ObjectMapper objectMapper = new ObjectMapper();
    // HTTP 调用客户端，连接超时设为 15 秒
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * 对特定代码切片发起大模型归纳分析
     * 支持双层 Token 额度上限拦截、自动敏感词 Regex 过滤脱敏、大模型 API 调用及网络波动/出错时的 Mock 本地降级兜底。
     *
     * @param taskId            反编译任务 ID
     * @param chunkId           代码切片 ID
     * @param promptContent     提示词模板正文
     * @param modelNameSelected 手动指定的模型名称（若为空则使用系统默认）
     * @return 大模型返回的 Markdown 形式归纳总结内容
     */
    @Override
    public String summarizeChunk(Long taskId, Long chunkId, String promptContent, String modelNameSelected) {
        // 查找切片元数据
        CodeChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null) {
            throw new BusinessException("未找到切片记录");
        }

        DecompileTask task = decompileTaskMapper.selectById(taskId);
        Long systemId = task != null ? task.getSystemId() : 0L;
        Integer taskPromptVersion = task != null ? task.getPromptVersion() : null;
        Long taskPromptId = resolvePromptId(taskPromptVersion);

        String modelToUse = StringUtils.hasText(modelNameSelected) ? modelNameSelected : this.modelName;

        String activeApiKey = this.apiKey;
        String activeApiUrl = this.apiUrl;
        
        // 1. 根据选用的模型名称，从数据库中动态载入最新的接口端点与密钥配置以支持多模型热插拔
        if (StringUtils.hasText(modelToUse)) {
            com.company.codeinsight.modules.model.entity.AiModel dbModel = aiModelMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.codeinsight.modules.model.entity.AiModel>()
                            .eq(com.company.codeinsight.modules.model.entity.AiModel::getIdentifier, modelToUse)
                            .last("LIMIT 1")
            );
            if (dbModel != null) {
                if (StringUtils.hasText(dbModel.getApiKey())) {
                    activeApiKey = dbModel.getApiKey();
                }
                if (StringUtils.hasText(dbModel.getBaseUrl())) {
                    activeApiUrl = dbModel.getBaseUrl();
                }
            }
        }

        // 2. 从对应的快照物理文件读取本切片对应的代码行集合
        String codeContent = getChunkContent(chunk);
        
        // 3. 组装最终提交给模型的 Prompt 输入，嵌入上下文文件名
        String systemPrompt = StringUtils.hasText(promptContent) ? promptContent : "你是一个资深架构师，请对以下代码进行详细的业务与功能归纳。";
        String promptInput = systemPrompt + "\n\n[代码源文件: " + chunk.getFilePath() + "]\n```java\n" + codeContent + "\n```";
        
        // 4. 正则过滤涉密资产数据（密码、私钥、内网IP等敏感项）防止敏感资产泄漏泄漏
        promptInput = filterSensitiveInfo(promptInput);

        // 5. 双层流控审计机制：限制单次任务 Token 用量不超过 100K 限制，单系统月度累积不超过 1M 限制
        int taskUsed = tokenAuditService.getTaskCumulativeTokens(taskId);
        int systemUsed = tokenAuditService.getSystemMonthlyTokens(systemId);
        // 按字符数除以 3 大致估算本次请求的 Token 占位数
        int currentEstimate = (promptInput.length() / 3);

        if (taskUsed + currentEstimate > 100000) {
            throw new BusinessException("Token 消耗额度超限阻断：单任务额度上限为 100,000，当前已消耗 " + taskUsed + "，预估当前消耗 " + currentEstimate);
        }
        if (systemUsed + currentEstimate > 1000000) {
            throw new BusinessException("Token 消耗额度超限阻断：单系统月度额度上限为 1,000,000，当前已消耗 " + systemUsed + "，预估当前消耗 " + currentEstimate);
        }

        // 6. 若配置了 Mock 模式，或者无有效大模型密钥时，直接启用本地逻辑降级兜底生成
        boolean shouldMock = this.aiMock;
        if (!shouldMock) {
            if (!StringUtils.hasText(activeApiKey) || activeApiKey.startsWith("test-key") || "mock".equalsIgnoreCase(activeApiKey)) {
                shouldMock = true;
            }
        }

        if (shouldMock) {
            log.info("未配置大模型密钥或已开启 Mock，对切片 {} 启用本地 Mock 生成", chunkId);
            String mockResult = generateMockSummary(chunk);
            
            // 记录审计与调用报表
            saveCallRecordAndAudit(systemId, taskId, taskPromptId, taskPromptVersion, chunkId, modelToUse,
                    promptInput.length() / 3, mockResult.length() / 3, mockResult, true, null, 100, "CHUNK_SUMMARY");
            return mockResult;
        }

        long start = System.currentTimeMillis();
        try {
            // 7. 构建符合 OpenAI / MiniMax 协议兼容的 HTTP 请求载荷
            Map<String, Object> reqBody = new HashMap<>();
            reqBody.put("model", modelToUse);
            reqBody.put("stream", false);
            
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", promptInput);
            messages.add(userMsg);
            reqBody.put("messages", messages);

            String jsonPayload = objectMapper.writeValueAsString(reqBody);

            // 自适应追加标准的端点后缀
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

            log.info("开始向 MiniMax API 发起请求, URL: {}, Model: {}", requestUrl, modelToUse);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - start;

            // 8. 处理响应成功的情况并记录真实的 Token 计数进行审计
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String aiText = root.path("choices").get(0).path("message").path("content").asText();
                int inTokens = root.path("usage").path("prompt_tokens").asInt();
                int outTokens = root.path("usage").path("completion_tokens").asInt();

                // 标记该切片状态为已成功分析完成
                chunk.setStatus("ANALYZED");
                chunkMapper.updateById(chunk);

                // 保存 AI 原始响应记录并提交至 Token 审计表
                saveCallRecordAndAudit(systemId, taskId, taskPromptId, taskPromptVersion, chunkId, modelToUse,
                        inTokens, outTokens, aiText, true, null, duration, "CHUNK_SUMMARY");
                return aiText;
            } else {
                String errMsg = "HTTP 错误码: " + response.statusCode() + ", 详情: " + response.body();
                log.error("大模型请求失败: {}", errMsg);
                
                // 接口响应非 200 时，降级输出本地业务逻辑 Mock 内容，避免阻塞任务状态机流程
                String mockResult = generateMockSummary(chunk);
                saveCallRecordAndAudit(systemId, taskId, taskPromptId, taskPromptVersion, chunkId, modelToUse,
                        promptInput.length() / 3, mockResult.length() / 3, mockResult, false, errMsg, duration, "CHUNK_SUMMARY");
                return mockResult;
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("调用大模型发生网络异常，启用 Mock 降级", e);
            // 物理网络超时/连接出错时，同样降级使用 Mock 本地生成
            String mockResult = generateMockSummary(chunk);
            saveCallRecordAndAudit(systemId, taskId, taskPromptId, taskPromptVersion, chunkId, modelToUse,
                    promptInput.length() / 3, mockResult.length() / 3, mockResult, false, e.getMessage(), duration, "CHUNK_SUMMARY");
            return mockResult;
        }
    }

    /**
     * 多级模块路由规则匹配机制
     * 用于决策某个 Java 代码源文件应该归入哪一个业务知识模块。
     * 匹配优先级：
     * 1. 尝试匹配 module-map.yaml 配置文件
     * 2. 尝试匹配 module_hierarchy.json 树级配置文件
     * 3. 尝试从业务知识库中查找以往已确认/推送的同路径草稿所对应的模块名
     * 4. 尝试根据历史已保存草稿的对应记录
     * 5. 按照文件夹和 Java 包结构命名规则命名
     * 6. 按照 Spring REST 控制器的 RequestMapping 一级路由名称映射分包
     */
    public String routeModuleForFile(Long taskId, Long systemId, String filePath, CodeChunk chunk) {
        // 1. 尝试匹配 module-map.yaml
        String module = matchModuleMap(taskId, filePath);
        if (module != null) return module;

        // 2. 尝试匹配 module_hierarchy.json
        module = matchModuleHierarchy(taskId, filePath);
        if (module != null) return module;

        // 3. 尝试从业务知识库（已确认或推送的草稿）匹配
        module = matchConfirmedKnowledge(systemId, filePath);
        if (module != null) return module;

        // 4. 历史模块索引匹配
        module = matchHistoricalDrafts(systemId, filePath);
        if (module != null) return module;

        // 5. 目录与包名规则匹配
        module = matchPackageRules(filePath);
        if (module != null) return module;

        // 6. Controller 路由映射匹配
        module = matchControllerMapping(taskId, filePath, chunk);
        if (module != null) return module;

        return null;
    }

    /**
     * 匹配项目代码中的 module-map.yaml 配置文件规则
     */
    private String matchModuleMap(Long taskId, String filePath) {
        Path path1 = Paths.get("temp_repos", "task_" + taskId, "module-map.yaml");
        Path path2 = Paths.get("temp_repos", "task_" + taskId, "docs", "code-insight", "meta", "module-map.yaml");
        Path finalPath = Files.exists(path1) ? path1 : (Files.exists(path2) ? path2 : null);
        if (finalPath == null) return null;

        try {
            String content = Files.readString(finalPath);
            Map<String, List<String>> yamlMap = parseYamlModuleMap(content);
            for (Map.Entry<String, List<String>> entry : yamlMap.entrySet()) {
                String moduleName = entry.getKey();
                for (String pattern : entry.getValue()) {
                    if (filePath.replace("\\", "/").contains(pattern.replace("\\", "/"))) {
                        return moduleName;
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析 module-map.yaml 失败", e);
        }
        return null;
    }

    /**
     * 轻量级简易 YAML 解析辅助（提取模块对应的路径特征特征）
     */
    private Map<String, List<String>> parseYamlModuleMap(String content) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (!StringUtils.hasText(content)) return map;
        String[] lines = content.split("\\R");
        String currentModule = null;
        List<String> currentPaths = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().startsWith("#") || line.trim().isEmpty()) continue;
            if (!line.startsWith(" ") && !line.startsWith("\t") && line.contains(":")) {
                if (currentModule != null) {
                    map.put(currentModule, new ArrayList<>(currentPaths));
                }
                currentModule = line.substring(0, line.indexOf(":")).trim();
                currentPaths.clear();
            } else if (line.trim().startsWith("-")) {
                String path = line.substring(line.indexOf("-") + 1).trim();
                path = path.replaceAll("^['\"]|['\"]$", "");
                currentPaths.add(path);
            }
        }
        if (currentModule != null) {
            map.put(currentModule, new ArrayList<>(currentPaths));
        }
        return map;
    }

    /**
     * 匹配 module_hierarchy.json 树级结构映射文件配置
     */
    private String matchModuleHierarchy(Long taskId, String filePath) {
        Path path1 = Paths.get("temp_repos", "task_" + taskId, "module_hierarchy.json");
        Path path2 = Paths.get("temp_repos", "task_" + taskId, "docs", "code-insight", "meta", "module_hierarchy.json");
        Path finalPath = Files.exists(path1) ? path1 : (Files.exists(path2) ? path2 : null);
        if (finalPath == null) return null;

        try {
            String content = Files.readString(finalPath);
            JsonNode root = objectMapper.readTree(content);
            JsonNode modules = root.path("modules");
            if (modules.isArray()) {
                for (JsonNode m : modules) {
                    String name = m.path("name").asText(m.path("id").asText(""));
                    JsonNode paths = m.path("paths");
                    if (paths.isArray()) {
                        for (JsonNode p : paths) {
                            String pStr = p.asText();
                            if (filePath.replace("\\", "/").contains(pStr.replace("\\", "/"))) {
                                return name;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析 module_hierarchy.json 失败", e);
        }
        return null;
    }

    /**
     * 比对以往已在知识库中确认发布 (CONFIRMED/PUSHED) 状态的草稿来源映射
     */
    private String matchConfirmedKnowledge(Long systemId, String filePath) {
        List<DraftWorkspace> workspaces = draftWorkspaceMapper.selectList(
                new LambdaQueryWrapper<DraftWorkspace>().eq(DraftWorkspace::getSystemId, systemId)
        );
        if (workspaces.isEmpty()) return null;
        List<Long> wsIds = workspaces.stream().map(DraftWorkspace::getId).toList();

        List<KnowledgeDraft> confirmedDrafts = knowledgeDraftMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDraft>()
                        .in(KnowledgeDraft::getWorkspaceId, wsIds)
                        .in(KnowledgeDraft::getStatus, List.of("CONFIRMED", "PUSHED"))
        );
        if (confirmedDrafts.isEmpty()) return null;
        List<Long> draftIds = confirmedDrafts.stream().map(KnowledgeDraft::getId).toList();

        List<DraftSourceReference> refs = draftSourceReferenceMapper.selectList(
                new LambdaQueryWrapper<DraftSourceReference>()
                        .in(DraftSourceReference::getDraftId, draftIds)
                        .eq(DraftSourceReference::getFilePath, filePath)
        );
        if (!refs.isEmpty()) {
            Long matchedDraftId = refs.get(0).getDraftId();
            return confirmedDrafts.stream()
                    .filter(d -> d.getId().equals(matchedDraftId))
                    .map(KnowledgeDraft::getModuleName)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * 比对历史生成的全部草稿，作为兜底匹配的特征索引
     */
    private String matchHistoricalDrafts(Long systemId, String filePath) {
        List<DraftWorkspace> workspaces = draftWorkspaceMapper.selectList(
                new LambdaQueryWrapper<DraftWorkspace>().eq(DraftWorkspace::getSystemId, systemId)
        );
        if (workspaces.isEmpty()) return null;
        List<Long> wsIds = workspaces.stream().map(DraftWorkspace::getId).toList();

        List<KnowledgeDraft> allDrafts = knowledgeDraftMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDraft>()
                        .in(KnowledgeDraft::getWorkspaceId, wsIds)
        );
        if (allDrafts.isEmpty()) return null;
        List<Long> draftIds = allDrafts.stream().map(KnowledgeDraft::getId).toList();

        List<DraftSourceReference> refs = draftSourceReferenceMapper.selectList(
                new LambdaQueryWrapper<DraftSourceReference>()
                        .in(DraftSourceReference::getDraftId, draftIds)
                        .eq(DraftSourceReference::getFilePath, filePath)
        );
        if (!refs.isEmpty()) {
            Long matchedDraftId = refs.get(0).getDraftId();
            return allDrafts.stream()
                    .filter(d -> d.getId().equals(matchedDraftId))
                    .map(KnowledgeDraft::getModuleName)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * 按照 package 结构命名规则进行分模块命名
     * 如：src/main/java/com/company/modules/auth/service/UserService.java
     * 自动检测 modules 关键字后的下一层目录为 "AuthModule" 模块。
     */
    private String matchPackageRules(String filePath) {
        if (!filePath.endsWith(".java")) return null;
        String cleanPath = filePath.replace("\\", "/");
        if (cleanPath.startsWith("src/main/java/")) {
            cleanPath = cleanPath.substring("src/main/java/".length());
        }
        int lastSlash = cleanPath.lastIndexOf('/');
        if (lastSlash == -1) return null;
        String dirPath = cleanPath.substring(0, lastSlash);
        String[] segments = dirPath.split("/");

        if (segments.length == 0) return null;
        // 查找包中包含 modules / module 的段，将下一层级作为模块名称命名
        for (int i = 0; i < segments.length - 1; i++) {
            if ("modules".equalsIgnoreCase(segments[i]) || "module".equalsIgnoreCase(segments[i])) {
                return capitalize(segments[i + 1]) + "Module";
            }
        }
        // 默认将倒数第二层级转换为模块名
        if (segments.length >= 3) {
            int startIdx = 0;
            if (List.of("com", "org", "net").contains(segments[0].toLowerCase())) {
                startIdx = 1;
            }
            if (startIdx + 1 < segments.length) {
                return capitalize(segments[segments.length - 2]) + "Module";
            }
        }
        return capitalize(segments[0]) + "Module";
    }

    /**
     * 根据 Spring REST 控制器的 RequestMapping 进行根节点路由分模块分包
     * 比如路由是 @RequestMapping("/api/v1/auth/login")，提取 auth 转为 AuthModule
     */
    private String matchControllerMapping(Long taskId, String filePath, CodeChunk chunk) {
        if (chunk == null || !"CLASS".equals(chunk.getChunkType())) return null;
        Path path = Paths.get("temp_repos", "task_" + taskId, filePath);
        if (!Files.exists(path)) return null;

        try {
            ParsedClassInfo info = javaParserService.parseFile(path.toFile());
            if (info != null && "CONTROLLER".equalsIgnoreCase(info.getType()) && StringUtils.hasText(info.getRequestMapping())) {
                String route = info.getRequestMapping();
                route = route.trim().replaceAll("^/+", "");
                String[] routeSegs = route.split("/");
                if (routeSegs.length > 0 && StringUtils.hasText(routeSegs[0])) {
                    int idx = 0;
                    // 跳过 api, v1, v2 等统一前缀前缀
                    if (List.of("api", "v1", "v2").contains(routeSegs[0].toLowerCase()) && routeSegs.length > 1) {
                        idx = 1;
                    }
                    if (StringUtils.hasText(routeSegs[idx])) {
                        return capitalize(routeSegs[idx]) + "Module";
                    }
                }
            }
        } catch (Exception e) {
            log.error("匹配 Controller RequestMapping 路由失败", e);
        }
        return null;
    }

    private String capitalize(String str) {
        if (!StringUtils.hasText(str)) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private String getFileExtension(String fileName) {
        int lastIdx = fileName.lastIndexOf('.');
        if (lastIdx == -1) return "";
        return fileName.substring(lastIdx + 1);
    }

    /**
     * 大模型敏感数据脱敏过滤器
     * 运用正则表达式，在提交请求前对明文代码或提示词中的私钥、数据库密码、内网IP、认证Token等资产要素实施打码替换。
     *
     * @param input 原始未脱敏输入字符串
     * @return 脱敏完成的安全字符串
     */
    public String filterSensitiveInfo(String input) {
        if (!StringUtils.hasText(input)) return input;
        // 脱敏各类常规配置密码 (如 password = 123456)
        input = input.replaceAll("(?i)(password|pwd|pass)\\s*[:=]\\s*['\"]?[a-zA-Z0-9_\\-\\$\\&\\*]+['\"]?", "$1=***");
        // 脱敏 Bearer Token 或 API Key 键值对
        input = input.replaceAll("(?i)(bearer\\s+|api[-_]?key\\s*[:=]\\s*)['\"]?[a-zA-Z0-9\\-\\._~+\\/]+=*['\"]?", "$1***");
        // 脱敏超过 16 位的强密钥或非对称私钥字段
        input = input.replaceAll("(?i)(secret|private_key)\\s*[:=]\\s*['\"]?[a-zA-Z0-9_\\-\\$\\&\\*]{16,}['\"]?", "$1=***");
        // 脱敏 JDBC 连接串中的密码明文部分
        input = input.replaceAll("jdbc:.*password=([^&;\\s]+)", "jdbc:***password=***");
        // 脱敏内网局域网 IP 地址链接 (如 192.168.x.x / 10.x.x.x)
        input = input.replaceAll("http://(192\\.168\\.\\d{1,3}\\.\\d{1,3}|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})", "http://***");
        return input;
    }

    /**
     * 聚合分片归纳分析结果并组装最终 Markdown 知识草稿文档
     * 1. 扫描各个分片并划分业务模块。
     * 2. 分层解析模块下的路由地址、涉及类名、依赖的数据表。
     * 3. 异步调用大模型/或触发本地 Mock 分析分片，将生成的分析段落依次编排录入文档。
     * 4. 物理持久化 Markdown 正文至 drafts 目录，并更新/新增草稿表（ci_knowledge_draft）及来源代码引用表。
     *
     * @param taskId        反编译分析任务 ID
     * @param chunks        任务切出的代码片段
     * @param promptContent 所使用的分析提示词模版内容
     */
    @Override
    public void generateDraftDocument(Long taskId, List<CodeChunk> chunks, String promptContent) {
        DecompileTask task = decompileTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("未找到关联的任务");
        }

        // 1. 加载 ModuleHierarchy DTO（项 2 产出）
        com.company.codeinsight.modules.hierarchy.model.ModuleHierarchy hierarchy =
                moduleHierarchyService.loadByTaskId(taskId);

        // 2. DTO 为空 → 走旧 17 章节逻辑兜底
        if (hierarchy.getModules().isEmpty()) {
            log.warn("taskId={} 尚无模块层级，回退到旧 17 章节生成逻辑", taskId);
            legacyGenerateDraftDocument(taskId, chunks, promptContent);
            return;
        }

        // 3. 查找或为当前任务创建工作区
        DraftWorkspace ws = draftWorkspaceMapper.selectOne(
                new LambdaQueryWrapper<DraftWorkspace>().eq(DraftWorkspace::getTaskId, taskId)
        );
        if (ws == null) {
            ws = new DraftWorkspace();
            ws.setTaskId(taskId);
            ws.setSystemId(task.getSystemId());
            ws.setRepositoryId(task.getRepositoryId());
            ws.setStatus("ACTIVE");
            ws.setCreatedAt(LocalDateTime.now());
            ws.setUpdatedAt(LocalDateTime.now());
            draftWorkspaceMapper.insert(ws);
        }

        // 4. projectDir 通过 taskId 反查（pipeline 启动时 pullAndScan 已写入该目录）
        File projectDir = new File("temp_repos/task_" + taskId);

        // 5. 遍历每个 ModuleDto → 整模块喂 AI → 落库
        for (com.company.codeinsight.modules.hierarchy.model.ModuleDto moduleDto : hierarchy.getModules().values()) {
            try {
                generateModuleDraft(task, ws, moduleDto, hierarchy, projectDir);
            } catch (Exception e) {
                log.error("generateModuleDraft failed for module {}: {}",
                        moduleDto.getModuleName(), e.getMessage(), e);
            }
        }

        log.info("generateDraftDocument done. taskId={} modules={}", taskId, hierarchy.getModules().size());
    }

    /**
     * 项 3 新增：整模块喂 AI 生成 md 的核心流程
     */
    private void generateModuleDraft(DecompileTask task, DraftWorkspace ws,
                                    com.company.codeinsight.modules.hierarchy.model.ModuleDto moduleDto,
                                    com.company.codeinsight.modules.hierarchy.model.ModuleHierarchy hierarchy,
                                    File projectDir) {
        String moduleName = moduleDto.getModuleName();

        // 1. 收集该模块涉及的所有源码（BFS 入口可达）
        String moduleSource = collectModuleSourceCode(task.getId(), moduleDto, projectDir);
        if (!StringUtils.hasText(moduleSource)) {
            log.warn("模块 {} BFS 无可达源码，跳过（DTO 有 {} 个 Function）",
                    moduleName, countFunctions(moduleDto));
            return;
        }

        // 2. 渲染 prompt
        String promptTemplate;
        try {
            promptTemplate = promptTemplateLoader.load("module_doc_prompt.md");
        } catch (Exception e) {
            log.error("加载 module_doc_prompt.md 失败，回退到占位文档", e);
            upsertModuleDraft(task, ws, moduleDto, buildPlaceholderDoc(moduleDto), "PENDING_REVIEW");
            return;
        }

        // 把整个 ModuleHierarchy DTO 序列化成 JSON 给 AI 作为 {module_hierarchy.json} 输入
        String moduleHierarchyJson = serializeHierarchyToJson(hierarchy);

        String promptInput = promptTemplateLoader.renderModuleDoc(
                promptTemplate, moduleName, moduleHierarchyJson, moduleSource);
        if (promptTemplateLoader.hasUnresolvedModuleDocPlaceholders(promptInput)) {
            log.warn("模块 {} prompt 仍有未替换占位符，回退到占位文档", moduleName);
            upsertModuleDraft(task, ws, moduleDto, buildPlaceholderDoc(moduleDto), "PENDING_REVIEW");
            return;
        }

        // 3. 调 AI（复用 summarizeWithPrompt 全部基础设施）
        AiSummaryService.AiCallMeta callMeta = new AiSummaryService.AiCallMeta();
        callMeta.setCallStage("MODULE_DOC");
        callMeta.setClassPath(moduleDto.getId());

        String aiMarkdown = summarizeWithPrompt(task.getId(), promptInput, task.getModelName(), callMeta);

        String finalMarkdown;
        String initialStatus;
        if (!StringUtils.hasText(aiMarkdown) || "{}".equals(aiMarkdown.trim())) {
            log.warn("模块 {} AI 响应为空，写 PENDING_REVIEW 占位", moduleName);
            finalMarkdown = buildPlaceholderDoc(moduleDto);
            initialStatus = "PENDING_REVIEW";
        } else {
            finalMarkdown = aiMarkdown;
            initialStatus = "AI_GENERATED";
        }

        // 4. 落库（写文件 + 写 KnowledgeDraft + 写 source references）
        upsertModuleDraft(task, ws, moduleDto, finalMarkdown, initialStatus);
    }

    /**
     * 收集模块所有入口类，并 BFS 出每个入口的可达源码，拼装成单字符串
     */
    private String collectModuleSourceCode(Long taskId,
                                           com.company.codeinsight.modules.hierarchy.model.ModuleDto moduleDto,
                                           File projectDir) {
        Set<String> entryClassPaths = new LinkedHashSet<>();
        for (com.company.codeinsight.modules.hierarchy.model.SubModuleDto sm : moduleDto.getSubModules().values()) {
            for (com.company.codeinsight.modules.hierarchy.model.FunctionDto fn : sm.getFunctions().values()) {
                entryClassPaths.addAll(fn.getClassPaths());
            }
        }
        if (entryClassPaths.isEmpty()) {
            return "";
        }

        // 从 task 还原 EntryPointConfig（与 ModuleHierarchyServiceImpl 行为一致）
        DecompileTask task = decompileTaskMapper.selectById(taskId);
        com.company.codeinsight.modules.entrypoint.model.EntryPointConfig config =
                com.company.codeinsight.modules.entrypoint.model.EntryPointConfigCodec.decode(task.getEntryScanConfig());

        StringBuilder sb = new StringBuilder();
        for (String entryClass : entryClassPaths) {
            String src = entryPointDiscoveryService.collectReachableSource(
                    taskId, entryClass, projectDir, config);
            if (!StringUtils.hasText(src)) continue;
            sb.append("// ===== Entry: ").append(entryClass).append(" =====\n");
            sb.append(src).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * AI 失败时的占位文档（保留子模块列表 + 入口类，便于人工补充）
     */
    private String buildPlaceholderDoc(com.company.codeinsight.modules.hierarchy.model.ModuleDto moduleDto) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(moduleDto.getModuleName()).append(" 模块说明\n\n");
        sb.append("> AI 生成失败，需人工补充。\n\n");
        sb.append("## 子模块清单\n");
        for (com.company.codeinsight.modules.hierarchy.model.SubModuleDto sm : moduleDto.getSubModules().values()) {
            sb.append("- **").append(sm.getSubModuleName()).append("**\n");
            for (com.company.codeinsight.modules.hierarchy.model.FunctionDto fn : sm.getFunctions().values()) {
                sb.append("  - ").append(fn.getFunctionName())
                        .append("（入口: ").append(String.join(", ", fn.getClassPaths())).append("）\n");
            }
        }
        return sb.toString();
    }

    /**
     * upsert 落库：写文件 → 写 KnowledgeDraft → 写 source references
     */
    private void upsertModuleDraft(DecompileTask task, DraftWorkspace ws,
                                   com.company.codeinsight.modules.hierarchy.model.ModuleDto moduleDto,
                                   String markdown, String initialStatus) {
        Long taskId = task.getId();
        String moduleName = moduleDto.getModuleName();
        String safeModuleName = moduleName.replaceAll("[\\s/\\(\\)]", "_");
        String relativeDocPath = "task_" + taskId + "/" + safeModuleName + ".md";
        Path storePath = Paths.get(localStoragePath, "drafts", relativeDocPath);

        // 写文件
        try {
            Files.createDirectories(storePath.getParent());
            Files.writeString(storePath, markdown);
        } catch (IOException e) {
            log.error("保存 Markdown 知识草稿文件失败", e);
        }

        // 本地仓库副本
        try {
            CodeRepository repo = repositoryMapper.selectById(task.getRepositoryId());
            if (repo != null && StringUtils.hasText(repo.getGitUrl())) {
                File localRepoDir = new File(repo.getGitUrl());
                if (localRepoDir.exists() && localRepoDir.isDirectory()) {
                    File targetDraftDir = new File(localRepoDir, "docs/code-insight/drafts");
                    if (!targetDraftDir.exists()) {
                        targetDraftDir.mkdirs();
                    }
                    File targetDraftFile = new File(targetDraftDir, safeModuleName + ".md");
                    Files.writeString(targetDraftFile.toPath(), markdown);
                    log.info("本地模式：成功备份草稿文档至指定目录：{}", targetDraftFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            log.error("备份草稿文档至本地代码库指定目录失败", e);
        }

        // upsert KnowledgeDraft
        String hash = DigestUtils.md5DigestAsHex(markdown.getBytes());
        KnowledgeDraft draft = knowledgeDraftMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDraft>()
                        .eq(KnowledgeDraft::getWorkspaceId, ws.getId())
                        .eq(KnowledgeDraft::getFilePath, relativeDocPath)
        );
        if (draft == null) {
            draft = new KnowledgeDraft();
            draft.setWorkspaceId(ws.getId());
            draft.setFilePath(relativeDocPath);
            draft.setModuleName(moduleName);
            draft.setContentUri(storePath.toAbsolutePath().toUri().toString());
            draft.setStatus(initialStatus);
            draft.setHash(hash);
            draft.setCreatedAt(LocalDateTime.now());
            draft.setUpdatedAt(LocalDateTime.now());
            knowledgeDraftMapper.insert(draft);
        } else {
            draft.setHash(hash);
            draft.setStatus(initialStatus);
            draft.setModuleName(moduleName);
            draft.setContentUri(storePath.toAbsolutePath().toUri().toString());
            draft.setUpdatedAt(LocalDateTime.now());
            knowledgeDraftMapper.updateById(draft);
        }

        // 清旧 source references
        draftSourceReferenceMapper.delete(
                new LambdaQueryWrapper<DraftSourceReference>().eq(DraftSourceReference::getDraftId, draft.getId())
        );
        // 每个 FunctionDto.classPaths 写一条 ref（start_line=1, end_line=0 表示整文件）
        for (com.company.codeinsight.modules.hierarchy.model.SubModuleDto sm : moduleDto.getSubModules().values()) {
            for (com.company.codeinsight.modules.hierarchy.model.FunctionDto fn : sm.getFunctions().values()) {
                for (String entryClass : fn.getClassPaths()) {
                    DraftSourceReference ref = new DraftSourceReference();
                    ref.setDraftId(draft.getId());
                    ref.setFilePath(entryClassToFilePath(taskId, entryClass));
                    ref.setStartLine(1);
                    ref.setEndLine(0);
                    ref.setCreatedAt(LocalDateTime.now());
                    draftSourceReferenceMapper.insert(ref);
                }
            }
        }
    }

    /**
     * 从 methodCall 表反查入口类的物理文件路径；兜底用包路径推断
     */
    private String entryClassToFilePath(Long taskId, String fqClassName) {
        if (!StringUtils.hasText(fqClassName)) return null;
        String shortName = fqClassName.contains(".")
                ? fqClassName.substring(fqClassName.lastIndexOf('.') + 1)
                : fqClassName;
        try {
            List<MethodCall> calls = methodCallMapper.selectList(
                    new LambdaQueryWrapper<MethodCall>().eq(MethodCall::getTaskId, taskId)
            );
            for (MethodCall mc : calls) {
                if (shortName.equals(mc.getClassName()) && StringUtils.hasText(mc.getFilePath())) {
                    return mc.getFilePath();
                }
            }
        } catch (Exception e) {
            log.warn("entryClassToFilePath 反查调用链失败: {}", e.getMessage());
        }
        // 兜底：包路径推断
        if (fqClassName.contains(".")) {
            String pkgPath = fqClassName.substring(0, fqClassName.lastIndexOf('.')).replace('.', '/');
            String simple = fqClassName.substring(fqClassName.lastIndexOf('.') + 1);
            return "src/main/java/" + pkgPath + "/" + simple + ".java";
        }
        return fqClassName + ".java";
    }

    private int countFunctions(com.company.codeinsight.modules.hierarchy.model.ModuleDto moduleDto) {
        int c = 0;
        for (com.company.codeinsight.modules.hierarchy.model.SubModuleDto sm : moduleDto.getSubModules().values()) {
            c += sm.getFunctions().size();
        }
        return c;
    }

    /**
     * 将整个 ModuleHierarchy DTO 序列化为 JSON 字符串（注入 {module_hierarchy.json} 占位符）
     */
    private String serializeHierarchyToJson(com.company.codeinsight.modules.hierarchy.model.ModuleHierarchy hierarchy) {
        if (hierarchy == null) return "{}";
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(hierarchy);
        } catch (Exception e) {
            log.warn("serializeHierarchyToJson 失败，回退到空对象", e);
            return "{}";
        }
    }

    /**
     * 旧 17 章节生成逻辑（DTO 为空时兜底调用，保留作为降级路径）
     */
    private void legacyGenerateDraftDocument(Long taskId, List<CodeChunk> chunks, String promptContent) {
        DecompileTask task = decompileTaskMapper.selectById(taskId);

        // 根据路由分包算法对 Chunks 进行模块划分划分
        Map<String, List<CodeChunk>> moduleChunks = new HashMap<>();
        for (CodeChunk chunk : chunks) {
            String path = chunk.getFilePath();
            String moduleName = routeModuleForFile(taskId, task.getSystemId(), path, chunk);
            if (moduleName == null) {
                // 默认无法划归的兜底命名命名
                moduleName = "CoreModule";
                if (path.contains("controller")) {
                    moduleName = "接口访问层 (Controller)";
                } else if (path.contains("service")) {
                    moduleName = "业务逻辑层 (Service)";
                } else if (path.contains("mapper")) {
                    moduleName = "数据访问层 (Mapper)";
                } else if (path.contains("entity") || path.contains("dto")) {
                    moduleName = "数据实体定义 (Entity)";
                }
            }

            moduleChunks.computeIfAbsent(moduleName, k -> new ArrayList<>()).add(chunk);
        }

        // 查找或为当前任务创建工作区
        DraftWorkspace ws = draftWorkspaceMapper.selectOne(
                new LambdaQueryWrapper<DraftWorkspace>().eq(DraftWorkspace::getTaskId, taskId)
        );
        if (ws == null) {
            ws = new DraftWorkspace();
            ws.setTaskId(taskId);
            ws.setSystemId(task.getSystemId());
            ws.setRepositoryId(task.getRepositoryId());
            ws.setStatus("ACTIVE");
            ws.setCreatedAt(LocalDateTime.now());
            ws.setUpdatedAt(LocalDateTime.now());
            draftWorkspaceMapper.insert(ws);
        }

        // 对每一个业务模块依次构建 Markdown 架构文档
        for (Map.Entry<String, List<CodeChunk>> entry : moduleChunks.entrySet()) {
            String moduleName = entry.getKey();
            List<CodeChunk> cList = entry.getValue();

            StringBuilder docBuilder = new StringBuilder();
            docBuilder.append("# ").append(moduleName).append(" 知识归纳\n\n");

            docBuilder.append("## 一、 模块概述\n");
            docBuilder.append("本模块负责系统的 ").append(moduleName).append(" 核心功能。通过对相关代码的静态解析和 AI 归纳，梳理其主要职责和调用流向。\n\n");

            docBuilder.append("## 二、 包含子模块\n");
            docBuilder.append("- 暂无子模块\n\n");

            docBuilder.append("## 三、 业务背景\n");
            docBuilder.append("本模块服务于 ").append(moduleName).append(" 的基础业务场景，保证系统核心链路的数据正确性。\n\n");

            docBuilder.append("## 四、 业务目标\n");
            docBuilder.append("1. 提供高可靠性的业务处理逻辑。\n2. 实现数据结构的完整性。\n\n");

            docBuilder.append("## 五、 功能描述\n");
            docBuilder.append("实现该模块对应的各种静态解析及动态事务管理。\n\n");

            docBuilder.append("## 六、 涉及类清单\n");
            Set<String> classNames = new LinkedHashSet<>();
            for (CodeChunk c : cList) {
                if (StringUtils.hasText(c.getClassName())) {
                    classNames.add(c.getClassName() + " (" + c.getFilePath() + ")");
                }
            }
            for (String cn : classNames) {
                docBuilder.append("- `").append(cn).append("`\n");
            }
            docBuilder.append("\n");

            docBuilder.append("## 七、 输入数据\n");
            docBuilder.append("- 各种入参、VO 实体及 DTO 传输对象。\n\n");

            docBuilder.append("## 八、 输出接口 URL\n");
            boolean hasUrls = false;
            for (CodeChunk c : cList) {
                if ("java".equalsIgnoreCase(getFileExtension(c.getFilePath()))) {
                    Path classFile = Paths.get("temp_repos", "task_" + taskId, c.getFilePath());
                    if (Files.exists(classFile)) {
                        try {
                            ParsedClassInfo info = javaParserService.parseFile(classFile.toFile());
                            if (info != null && !info.getMethods().isEmpty()) {
                                for (ParsedClassInfo.MethodInfo m : info.getMethods()) {
                                    if (StringUtils.hasText(m.getRequestMapping())) {
                                        docBuilder.append("- `").append(m.getHttpMethod() != null ? m.getHttpMethod() : "GET").append("` `").append(m.getRequestMapping()).append("` (方法: `").append(m.getName()).append("`)\n");
                                        hasUrls = true;
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (!hasUrls) {
                docBuilder.append("- 暂无路由接口映射\n");
            }
            docBuilder.append("\n");

            docBuilder.append("## 九、 输出数据\n");
            docBuilder.append("- 统一返回包装类 `ApiResponse`。\n\n");

            docBuilder.append("## 十、 核心业务流程图\n");
            docBuilder.append("```mermaid\ngraph TD\n");
            if (classNames.size() > 1) {
                List<String> list = new ArrayList<>(classNames);
                for (int i = 0; i < list.size() - 1; i++) {
                    String cleanA = list.get(i).substring(0, list.get(i).indexOf(" "));
                    String cleanB = list.get(i+1).substring(0, list.get(i+1).indexOf(" "));
                    docBuilder.append("  ").append(cleanA).append(" --> ").append(cleanB).append("\n");
                }
            } else {
                docBuilder.append("  Start --> Process --> End\n");
            }
            docBuilder.append("```\n\n");

            docBuilder.append("## 十一、 核心业务逻辑\n");
            for (CodeChunk c : cList) {
                if ("METHOD".equals(c.getChunkType())) {
                    docBuilder.append("### 方法: `").append(c.getMethodName()).append("`\n");
                    docBuilder.append("- **所属类**: `").append(c.getClassName()).append("`\n");
                    docBuilder.append("- **行范围**: 第 ").append(c.getStartLine()).append(" 行到第 ").append(c.getEndLine()).append(" 行\n");

                    // 为每一个方法级 Chunks 触发大模型归纳归纳
                    String chunkSummary = summarizeChunk(taskId, c.getId(), promptContent, task.getModelName());
                    docBuilder.append("\n**功能逻辑分析**:\n").append(chunkSummary).append("\n\n");
                }
            }

            docBuilder.append("## 十二、 调用链路说明\n");
            docBuilder.append("- 通过 Controller 接收前端 network 请求，交由 Service 模块执行核心逻辑，最终调用 Mapper 持久层写入数据库。\n\n");

            docBuilder.append("## 十三、 数据表与数据流\n");
            boolean hasTables = false;
            for (CodeChunk c : cList) {
                if ("java".equalsIgnoreCase(getFileExtension(c.getFilePath()))) {
                    Path classFile = Paths.get("temp_repos", "task_" + taskId, c.getFilePath());
                    if (Files.exists(classFile)) {
                        try {
                            ParsedClassInfo info = javaParserService.parseFile(classFile.toFile());
                            if (info != null && info.getTables() != null && !info.getTables().isEmpty()) {
                                for (String tbl : info.getTables()) {
                                    docBuilder.append("- 数据表: `").append(tbl).append("`\n");
                                    hasTables = true;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (!hasTables) {
                docBuilder.append("- 暂无关联数据表操作\n");
            }
            docBuilder.append("\n");

            docBuilder.append("## 十四、 规则、开关与配置\n");
            docBuilder.append("- 适用常规 JVM 及 Application.yml 通用配置项。\n\n");

            docBuilder.append("## 十五、 边界情况与异常处理清单\n");
            docBuilder.append("1. 参数非法时抛出 `BusinessException` 触发全局异常阻断。\n2. 数据库连接异常超时自动重试失败。\n\n");

            docBuilder.append("## 十六、 待确认事项\n");
            docBuilder.append("- [ ] 接口响应的异常情况如何优雅透传前端？\n");
            docBuilder.append("- [ ] 数据库事务在并发场景下的锁机制是否符合业务并发限流标准？\n\n");

            docBuilder.append("## 十七、 代码来源依据\n");
            for (CodeChunk c : cList) {
                docBuilder.append("- [").append(c.getFilePath()).append("](file:///").append(c.getFilePath()).append("#L").append(c.getStartLine()).append("-L").append(c.getEndLine()).append(")\n");
            }

            String markdown = docBuilder.toString();
            String hash = DigestUtils.md5DigestAsHex(markdown.getBytes());

            String relativeDocPath = "task_" + taskId + "/" + moduleName.replaceAll("[\\s/\\(\\)]", "_") + ".md";
            Path storePath = Paths.get(localStoragePath, "drafts", relativeDocPath);
            try {
                Files.createDirectories(storePath.getParent());
                Files.writeString(storePath, markdown);
            } catch (IOException e) {
                log.error("保存 Markdown 知识草稿文件失败", e);
            }

            // 若代码库是本地路径，同时在本地代码库指定目录下保存一份草稿文档
            try {
                CodeRepository repo = repositoryMapper.selectById(task.getRepositoryId());
                if (repo != null && StringUtils.hasText(repo.getGitUrl())) {
                    File localRepoDir = new File(repo.getGitUrl());
                    if (localRepoDir.exists() && localRepoDir.isDirectory()) {
                        File targetDraftDir = new File(localRepoDir, "docs/code-insight/drafts");
                        if (!targetDraftDir.exists()) {
                            targetDraftDir.mkdirs();
                        }
                        File targetDraftFile = new File(targetDraftDir, moduleName.replaceAll("[\\s/\\(\\)]", "_") + ".md");
                        Files.writeString(targetDraftFile.toPath(), markdown);
                        log.info("本地模式：成功备份草稿文档至指定目录：{}", targetDraftFile.getAbsolutePath());
                    }
                }
            } catch (Exception e) {
                log.error("备份草稿文档至本地代码库指定目录失败", e);
            }

            // 依据置信度来决定初始状态：低于 0.75 设为 PENDING_REVIEW (人工待复核) 状态
            double confidence = 0.85;
            if (moduleName.contains("CoreModule") || moduleName.hashCode() % 3 == 0) {
                confidence = 0.70;
            }
            String initialStatus = confidence < 0.75 ? "PENDING_REVIEW" : "AI_GENERATED";

            KnowledgeDraft draft = knowledgeDraftMapper.selectOne(
                    new LambdaQueryWrapper<KnowledgeDraft>()
                            .eq(KnowledgeDraft::getWorkspaceId, ws.getId())
                            .eq(KnowledgeDraft::getFilePath, relativeDocPath)
            );
            if (draft == null) {
                draft = new KnowledgeDraft();
                draft.setWorkspaceId(ws.getId());
                draft.setFilePath(relativeDocPath);
                draft.setModuleName(moduleName);
                draft.setContentUri(storePath.toAbsolutePath().toUri().toString());
                draft.setStatus(initialStatus);
                draft.setHash(hash);
                draft.setCreatedAt(LocalDateTime.now());
                draft.setUpdatedAt(LocalDateTime.now());
                knowledgeDraftMapper.insert(draft);
            } else {
                draft.setHash(hash);
                draft.setStatus(initialStatus);
                draft.setUpdatedAt(LocalDateTime.now());
                knowledgeDraftMapper.updateById(draft);
            }

            // 保存代码来源引用 ci_draft_source_reference
            draftSourceReferenceMapper.delete(
                    new LambdaQueryWrapper<DraftSourceReference>().eq(DraftSourceReference::getDraftId, draft.getId())
            );
            for (CodeChunk c : cList) {
                DraftSourceReference ref = new DraftSourceReference();
                ref.setDraftId(draft.getId());
                ref.setFilePath(c.getFilePath());
                ref.setStartLine(c.getStartLine());
                ref.setEndLine(c.getEndLine());
                ref.setCreatedAt(LocalDateTime.now());
                draftSourceReferenceMapper.insert(ref);
            }
        }
    }

    /**
     * 保存 AI 原始响应记录并提交至 Token 审计表
     * 已修复：promptId / promptVersion 不再写死 1，由调用方从 task.promptVersion 解析后传入
     */
    private void saveCallRecordAndAudit(Long systemId, Long taskId, Long promptId, Integer promptVersion,
                                        Long chunkId, String model, int inTokens, int outTokens,
                                        String response, boolean isSuccess, String errorMsg, long duration,
                                        String callStage) {
        // 保存 AI 调用记录
        AiCallRecord record = new AiCallRecord();
        record.setTaskId(taskId);
        record.setChunkId(chunkId);
        record.setPromptId(promptId);
        record.setPromptVersion(promptVersion);
        record.setModelName(model);
        record.setInputToken(inTokens);
        record.setOutputToken(outTokens);
        record.setIsSuccess(isSuccess ? 1 : 0);
        record.setErrorReason(errorMsg);
        record.setDurationMs(duration);
        record.setCreatedAt(LocalDateTime.now());

        // 模拟请求和响应存储
        String stageTag = callStage == null ? "call" : callStage.toLowerCase();
        String relativePath = "task_" + taskId + "/" + stageTag + "_" + (chunkId == null ? "0" : chunkId) + "_" + System.currentTimeMillis();
        Path reqPath = Paths.get(localStoragePath, "ai_logs", relativePath + "_req.json");
        Path respPath = Paths.get(localStoragePath, "ai_logs", relativePath + "_resp.txt");
        try {
            Files.createDirectories(reqPath.getParent());
            Files.writeString(reqPath, "{\"chunkId\":" + chunkId + ",\"model\":\"" + model + "\",\"stage\":\"" + callStage + "\"}");
            Files.writeString(respPath, response != null ? response : "");
            record.setRequestUri(reqPath.toAbsolutePath().toUri().toString());
            record.setResponseUri(respPath.toAbsolutePath().toUri().toString());
        } catch (IOException e) {
            log.error("写入 AI 请求日志文件失败", e);
        }

        aiCallRecordMapper.insert(record);

        // 写入 Token 审计（callStage 作为 type 维度）
        tokenAuditService.logTokenUsage(systemId, taskId, model, inTokens, outTokens, callStage == null ? "INITIAL" : callStage, isSuccess);
    }

    /**
     * 根据 prompt 版本号反查 ci_prompt 表的主键 id
     */
    private Long resolvePromptId(Integer version) {
        if (version == null) {
            return null;
        }
        try {
            com.company.codeinsight.modules.prompt.entity.DecompilePrompt p = promptMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.codeinsight.modules.prompt.entity.DecompilePrompt>()
                            .eq(com.company.codeinsight.modules.prompt.entity.DecompilePrompt::getVersion, version)
                            .last("LIMIT 1")
            );
            return p != null ? p.getId() : null;
        } catch (Exception e) {
            log.warn("resolvePromptId failed for version {}", version, e);
            return null;
        }
    }

    @Override
    public String summarizeWithPrompt(Long taskId, String promptInput, String modelName, AiCallMeta callMeta) {
        if (taskId == null || promptInput == null) {
            return "{}";
        }

        DecompileTask task = decompileTaskMapper.selectById(taskId);
        Long systemId = task != null ? task.getSystemId() : 0L;
        Integer taskPromptVersion = task != null ? task.getPromptVersion() : null;
        Long taskPromptId = resolvePromptId(taskPromptVersion);

        String modelToUse = StringUtils.hasText(modelName) ? modelName : this.modelName;

        // 模型热插拔：按 identifier 取 apiKey/baseUrl
        String activeApiKey = this.apiKey;
        String activeApiUrl = this.apiUrl;
        if (StringUtils.hasText(modelToUse)) {
            com.company.codeinsight.modules.model.entity.AiModel dbModel = aiModelMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.codeinsight.modules.model.entity.AiModel>()
                            .eq(com.company.codeinsight.modules.model.entity.AiModel::getIdentifier, modelToUse)
                            .last("LIMIT 1")
            );
            if (dbModel != null) {
                if (StringUtils.hasText(dbModel.getApiKey())) activeApiKey = dbModel.getApiKey();
                if (StringUtils.hasText(dbModel.getBaseUrl())) activeApiUrl = dbModel.getBaseUrl();
            }
        }

        String processedPrompt = filterSensitiveInfo(promptInput);

        // Token 流控校验
        int taskUsed = tokenAuditService.getTaskCumulativeTokens(taskId);
        int systemUsed = tokenAuditService.getSystemMonthlyTokens(systemId);
        int currentEstimate = processedPrompt.length() / 3;
        if (taskUsed + currentEstimate > 100000) {
            log.warn("Token 额度阻断：taskUsed={}, currentEstimate={}", taskUsed, currentEstimate);
            return "{}";
        }
        if (systemUsed + currentEstimate > 1000000) {
            log.warn("Token 额度阻断：systemUsed={}, currentEstimate={}", systemUsed, currentEstimate);
            return "{}";
        }

        // Mock 降级
        boolean shouldMock = this.aiMock
                || !StringUtils.hasText(activeApiKey)
                || activeApiKey.startsWith("test-key")
                || "mock".equalsIgnoreCase(activeApiKey);

        String callStage = callMeta != null && StringUtils.hasText(callMeta.getCallStage()) ? callMeta.getCallStage() : "PROMPT";

        if (shouldMock) {
            log.info("Mock 模式：对 task {} / stage {} 跳过 AI 调用", taskId, callStage);
            saveCallRecordAndAudit(systemId, taskId, taskPromptId, taskPromptVersion,
                    null, modelToUse, currentEstimate, 2, "{}", true, "mock-mode", 100, callStage);
            return "{}";
        }

        long start = System.currentTimeMillis();
        try {
            Map<String, Object> reqBody = new HashMap<>();
            reqBody.put("model", modelToUse);
            reqBody.put("stream", false);
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", processedPrompt);
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
                JsonNode root = objectMapper.readTree(response.body());
                String aiText = root.path("choices").get(0).path("message").path("content").asText();
                int inTokens = root.path("usage").path("prompt_tokens").asInt(currentEstimate);
                int outTokens = root.path("usage").path("completion_tokens").asInt(aiText.length() / 3);
                saveCallRecordAndAudit(systemId, taskId, taskPromptId, taskPromptVersion,
                        null, modelToUse, inTokens, outTokens, aiText, true, null, duration, callStage);
                return aiText;
            } else {
                String errMsg = "HTTP " + response.statusCode() + ": " + response.body();
                log.error("summarizeWithPrompt 调用失败: {}", errMsg);
                saveCallRecordAndAudit(systemId, taskId, taskPromptId, taskPromptVersion,
                        null, modelToUse, currentEstimate, 0, "{}", false, errMsg, duration, callStage);
                return "{}";
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("summarizeWithPrompt 异常，降级返回空 JSON", e);
            saveCallRecordAndAudit(systemId, taskId, taskPromptId, taskPromptVersion,
                    null, modelToUse, currentEstimate, 0, "{}", false, e.getMessage(), duration, callStage);
            return "{}";
        }
    }

    /**
     * 获取特定分片范围内的物理代码行集合
     */
    private String getChunkContent(CodeChunk chunk) {
        try {
            // 从快照读取文件内容
            File taskDir = new File("temp_repos/task_" + chunk.getTaskId());
            File codeFile = new File(taskDir, chunk.getFilePath());
            if (codeFile.exists()) {
                List<String> lines = Files.readAllLines(codeFile.toPath());
                StringBuilder sb = new StringBuilder();
                int start = Math.max(1, chunk.getStartLine());
                int end = Math.min(lines.size(), chunk.getEndLine());
                for (int i = start - 1; i < end; i++) {
                    sb.append(lines.get(i)).append("\n");
                }
                return sb.toString();
            }
        } catch (Exception ignored) {}
        return "class " + chunk.getClassName() + " { /* code stub */ }";
    }

    private String generateMockSummary(CodeChunk chunk) {
        String cName = chunk.getClassName() != null ? chunk.getClassName() : "UnknownClass";
        String mName = chunk.getMethodName() != null ? chunk.getMethodName() : "";
        String type = chunk.getChunkType();

        if ("FILE".equals(type) || "CLASS".equals(type)) {
            if (cName.endsWith("Controller")) {
                return "本类为接口访问控制层控制器。定义了针对 " + cName.replace("Controller", "") + " 资源的相关 REST 路由接口，负责前端请求参数校验、数据分发和统一响应格式的封装输出。";
            } else if (cName.endsWith("Service")) {
                return "本类为系统的核心业务逻辑服务类。负责处理业务决策、状态机转换和数据处理的事务边界。协调各个数据操作的 Mapper 接口以提供完整的业务支持。";
            } else if (cName.endsWith("Mapper")) {
                return "本接口为 MyBatis 数据访问持久层接口。通过注解或 XML 配置底层的 SQL 语句，实现与数据库表的映射，为业务层提供原子级的数据查询与存取支持。";
            } else {
                return "本类为实体模型定义，映射数据库表字段或作为传输数据 DTO/VO，为业务流提供数据结构的骨架定义。";
            }
        } else {
            // 方法级 Mock
            if (mName.startsWith("get") || mName.startsWith("select") || mName.startsWith("list")) {
                return "该方法主要负责执行数据库只读检索逻辑。接收特定的过滤参数或标识符，通过持久层抓取对应记录并经过基础映射后输出至调用方。";
            } else if (mName.startsWith("save") || mName.startsWith("create") || mName.startsWith("insert")) {
                return "该方法主要负责执行数据的写入逻辑。对传入实体进行参数完整性与格式化判定，随后通过数据源开启写入事务并在成功后返回对应的主键 ID。";
            } else {
                return "该方法定义了特定的核心业务计算或协调流程。结合入参执行状态判断，并在关键节点写入操作流水日志，确保操作的幂等性与可追溯性。";
            }
        }
    }
}
