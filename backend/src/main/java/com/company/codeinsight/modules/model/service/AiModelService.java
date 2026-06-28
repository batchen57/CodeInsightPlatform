package com.company.codeinsight.modules.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.model.dto.AiModelMetricSummary;
import com.company.codeinsight.modules.model.dto.AiModelMetricTrendPoint;
import com.company.codeinsight.modules.model.dto.AiModelTestResult;
import com.company.codeinsight.modules.model.entity.AiModel;
import java.util.List;

/**
 * AI 大语言模型配置与选取服务接口
 * 负责定义 AI 模型库列表获取、排他性默认大模型设置、以及增改逻辑。
 */
public interface AiModelService extends IService<AiModel> {
    
    /**
     * 获取所有配置的大模型列表，按 sortOrder 升序排列
     *
     * @return 模型实体列表
     */
    List<AiModel> listAllModelsSorted();

    /**
     * 新增大模型配置
     * 若设置该模型为默认模型，会自动清除并重置其它原有模型为非默认状态（排他）。
     *
     * @param model 模型配置参数实体
     * @return 是否成功
     */
    boolean saveModel(AiModel model);

    /**
     * 更新已有的大模型配置参数
     * 若本模型被设置为默认状态，会自动取消其它模型的默认身份。
     *
     * @param model 模型配置参数实体
     * @return 是否成功
     */
    boolean updateModel(AiModel model);

    /**
     * 切换大模型启用状态（0-停用，1-启用）
     * 停用时不影响历史调用记录
     *
     * @param id     模型 ID
     * @param status 目标状态
     */
    void changeStatus(Long id, Integer status);

    /**
     * 按模型标识汇总累计调用次数、Token 与预估成本。
     */
    List<AiModelMetricSummary> listMetricSummaries();

    /**
     * 查询指定模型最近 N 天调用趋势，按自然日补齐空值。
     */
    List<AiModelMetricTrendPoint> getMetricTrend(Long id, Integer days);

    /**
     * 测试模型连接可用性。
     */
    AiModelTestResult testModelConnection(Long id);
}

