package com.company.codeinsight.modules.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.model.dto.AiModelMetricSummary;
import com.company.codeinsight.modules.model.dto.AiModelMetricTrendPoint;
import com.company.codeinsight.modules.model.dto.AiModelTestResult;
import com.company.codeinsight.modules.model.entity.AiModel;
import com.company.codeinsight.modules.model.mapper.AiModelMapper;
import com.company.codeinsight.modules.model.service.AiModelService;
import com.company.codeinsight.modules.token.mapper.TokenUsageAuditMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 大语言模型配置与选取服务实现类
 * 负责模型按权重展示排序，以及写入/更新大模型时排他性地保证仅有一个默认大模型。
 */
@Service
public class AiModelServiceImpl extends ServiceImpl<AiModelMapper, AiModel> implements AiModelService {

    @Autowired
    private TokenUsageAuditMapper tokenUsageAuditMapper;

    /**
     * 全局 Mock AI 只影响任务/提示词等 AI 业务链路。
     * 模型配置页的“测试连接”必须验证当前模型自身的真实接口配置，不能被该开关短路。
     */
    @SuppressWarnings("unused")
    @Value("${code-insight.ai.mock:true}")
    private boolean mockAiEnabled;

    /**
     * 按照 sortOrder 升序、主键自增 ID 降序排列获取全部 AI 模型
     */
    @Override
    public List<AiModel> listAllModelsSorted() {
        LambdaQueryWrapper<AiModel> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(AiModel::getSortOrder)
                    .orderByDesc(AiModel::getId);
        return this.list(queryWrapper);
    }

    /**
     * 新增大语言模型
     * 若本模型声明为默认（isDefault=true），则自动将其它已存在的默认大模型降级。
     */
    @Override
    @Transactional
    public boolean saveModel(AiModel model) {
        if ("true".equals(model.getIsDefault())) {
            clearOtherDefaults(null);
        }
        // 新建模型默认启用
        if (model.getStatus() == null) {
            model.setStatus(1);
        }
        return this.save(model);
    }

    /**
     * 修改已有大模型
     * 若修改后本模型被置为默认，自动将其它已存在大模型设为非默认。
     */
    @Override
    @Transactional
    public boolean updateModel(AiModel model) {
        if ("true".equals(model.getIsDefault())) {
            clearOtherDefaults(model.getId());
        }
        if (!StringUtils.hasText(model.getApiKey()) && model.getId() != null) {
            AiModel existing = this.getById(model.getId());
            if (existing != null) {
                model.setApiKey(existing.getApiKey());
            }
        }
        return this.updateById(model);
    }

    /**
     * 切换大模型启用状态（0-停用，1-启用）。
     * 停用时不影响历史引用（ai_call_record / token_usage_audit 的 model_name 是字符串外键）。
     * 停用时如果是默认模型，必须先把默认身份让出来——避免脏数据。
     */
    @Override
    @Transactional
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态非法，只能为 0 或 1");
        }
        AiModel model = this.getById(id);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        // 停用时如果是默认模型，强制让出默认身份
        if (status == 0 && "true".equals(model.getIsDefault())) {
            model.setIsDefault("false");
        }
        model.setStatus(status);
        this.updateById(model);
    }

    @Override
    public List<AiModelMetricSummary> listMetricSummaries() {
        return tokenUsageAuditMapper.selectModelMetricSummaries();
    }

    @Override
    public List<AiModelMetricTrendPoint> getMetricTrend(Long id, Integer days) {
        AiModel model = this.getById(id);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        int normalizedDays = days == null ? 7 : Math.max(1, Math.min(days, 30));
        LocalDate startDate = LocalDate.now().minusDays(normalizedDays - 1L);
        LocalDateTime startAt = startDate.atStartOfDay();
        LocalDateTime endAt = LocalDate.now().plusDays(1).atStartOfDay();

        Map<String, AiModelMetricTrendPoint> trendMap = new LinkedHashMap<>();
        for (int i = 0; i < normalizedDays; i++) {
            String date = startDate.plusDays(i).toString();
            AiModelMetricTrendPoint point = new AiModelMetricTrendPoint();
            point.setDate(date);
            point.setCalls(0L);
            point.setTokens(0L);
            point.setCost(BigDecimal.ZERO);
            trendMap.put(date, point);
        }

        List<AiModelMetricTrendPoint> rows =
            tokenUsageAuditMapper.selectModelMetricTrend(model.getIdentifier(), startAt, endAt);
        for (AiModelMetricTrendPoint row : rows) {
            if (trendMap.containsKey(row.getDate())) {
                trendMap.put(row.getDate(), row);
            }
        }
        return List.copyOf(trendMap.values());
    }

    @Override
    public AiModelTestResult testModelConnection(Long id) {
        AiModel model = this.getById(id);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        long started = System.currentTimeMillis();
        if (!StringUtils.hasText(model.getBaseUrl())) {
            return buildTestResult(false, started, "模型接口地址未配置", null, model.getApiKey());
        }
        if (!StringUtils.hasText(model.getApiKey())) {
            return buildTestResult(false, started, "模型 API Key 未配置", null, null);
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveChatCompletionsUrl(model.getBaseUrl())))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + model.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(buildPingPayload(model.getIdentifier()), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            String message = success ? "模型连接测试通过" : "模型连接测试失败，HTTP " + response.statusCode();
            return buildTestResult(success, started, message, response.body(), model.getApiKey());
        } catch (Exception ex) {
            return buildTestResult(false, started, "模型连接测试失败: " + ex.getMessage(), null, model.getApiKey());
        }
    }

    /**
     * 辅助排他性机制：批量清空其它大模型的默认标志
     *
     * @param excludeId 需要排斥不清除的当前模型 ID
     */
    private void clearOtherDefaults(Long excludeId) {
        LambdaQueryWrapper<AiModel> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiModel::getIsDefault, "true");
        if (excludeId != null) {
            queryWrapper.ne(AiModel::getId, excludeId);
        }
        List<AiModel> defaults = this.list(queryWrapper);
        for (AiModel m : defaults) {
            m.setIsDefault("false");
            this.updateById(m);
        }
    }

    private AiModelTestResult buildTestResult(boolean success, long started, String message, String summary, String apiKey) {
        AiModelTestResult result = new AiModelTestResult();
        result.setSuccess(success);
        result.setDurationMs(System.currentTimeMillis() - started);
        result.setMessage(maskSecret(message, apiKey));
        result.setResponseSummary(maskSecret(truncate(summary), apiKey));
        return result;
    }

    private String resolveChatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private String buildPingPayload(String identifier) {
        String modelName = StringUtils.hasText(identifier) ? identifier : "default";
        return """
            {"model":"%s","messages":[{"role":"user","content":"ping"}],"max_tokens":8}
            """.formatted(escapeJson(modelName)).trim();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }

    private String maskSecret(String value, String apiKey) {
        if (value == null || !StringUtils.hasText(apiKey)) {
            return value;
        }
        return value.replace(apiKey, "***");
    }
}

