import React from 'react';
import { Col, Drawer, Empty, Row, Space, Spin, Statistic, Tag, Typography } from 'antd';
import {
  ApiOutlined,
  DollarCircleOutlined,
  LineChartOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import type { AiModel, AiModelMetricSummary, AiModelMetricTrendPoint } from '../../types';

const { Text, Title } = Typography;

interface Props {
  open: boolean;
  model: AiModel | null;
  summary?: AiModelMetricSummary;
  trend: AiModelMetricTrendPoint[];
  loading: boolean;
  onClose: () => void;
}

const formatCompact = (value?: number) => {
  const safeValue = Number(value ?? 0);
  return new Intl.NumberFormat('zh-CN', {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(safeValue);
};

const formatCost = (value?: number) => `$${Number(value ?? 0).toFixed(4)}`;

const buildMiniLineOption = (
  name: string,
  color: string,
  dates: string[],
  values: number[],
  valueFormatter: (value: number) => string = formatCompact,
) => ({
  color: [color],
  tooltip: {
    trigger: 'axis',
    valueFormatter,
  },
  grid: { top: 16, right: 12, bottom: 22, left: 36 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: dates,
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#e5e7eb' } },
    axisLabel: { color: '#8a94a6', fontSize: 11 },
  },
  yAxis: {
    type: 'value',
    splitNumber: 2,
    axisLabel: {
      color: '#8a94a6',
      fontSize: 11,
      formatter: (value: number) => formatCompact(value),
    },
    splitLine: { lineStyle: { color: '#edf2f7' } },
  },
  series: [
    {
      name,
      type: 'line',
      data: values,
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 2 },
      areaStyle: { color: `${color}1A` },
    },
  ],
});

const ModelMetricsDrawer: React.FC<Props> = ({
  open,
  model,
  summary,
  trend,
  loading,
  onClose,
}) => {
  const dates = trend.map((item) => item.date.slice(5));
  const callValues = trend.map((item) => item.calls);
  const tokenValues = trend.map((item) => item.tokens);
  const costValues = trend.map((item) => Number(item.cost ?? 0));

  return (
    <Drawer
      title="模型调用洞察"
      open={open}
      onClose={onClose}
      width={760}
      destroyOnHidden
    >
      {!model ? (
        <Empty description="请选择模型" />
      ) : (
        <Spin spinning={loading}>
          <Space direction="vertical" size={18} style={{ width: '100%' }}>
            <div>
              <Space size={8} wrap>
                <Title level={4} style={{ margin: 0 }}>{model.name}</Title>
                <Tag color="blue">{model.provider}</Tag>
                <Text code>{model.identifier}</Text>
              </Space>
              <div style={{ marginTop: 8 }}>
                <Text type="secondary">{model.description || '暂无模型说明'}</Text>
              </div>
            </div>

            <Row gutter={[12, 12]} className="ci-model-metric-summary">
              <Col xs={24} sm={8}>
                <Statistic
                  title="总调用"
                  value={summary?.totalCalls ?? 0}
                  formatter={(value) => formatCompact(Number(value))}
                  prefix={<ApiOutlined />}
                />
              </Col>
              <Col xs={24} sm={8}>
                <Statistic
                  title="总 Token"
                  value={summary?.totalTokens ?? 0}
                  formatter={(value) => formatCompact(Number(value))}
                  prefix={<LineChartOutlined />}
                />
              </Col>
              <Col xs={24} sm={8}>
                <Statistic
                  title="总成本"
                  value={summary?.totalCost ?? 0}
                  precision={4}
                  prefix={<DollarCircleOutlined />}
                  formatter={(value) => formatCost(Number(value))}
                />
              </Col>
            </Row>

            <section className="ci-model-metric-section">
              <div className="ci-model-metric-section-title">最近 7 天趋势</div>
              <Row gutter={[12, 12]}>
                <Col xs={24} lg={8}>
                  <div className="ci-model-mini-chart">
                    <Text strong>调用</Text>
                    <ReactECharts
                      option={buildMiniLineOption('调用', '#2563eb', dates, callValues)}
                      style={{ height: 150 }}
                    />
                  </div>
                </Col>
                <Col xs={24} lg={8}>
                  <div className="ci-model-mini-chart">
                    <Text strong>Token</Text>
                    <ReactECharts
                      option={buildMiniLineOption('Token', '#0891b2', dates, tokenValues)}
                      style={{ height: 150 }}
                    />
                  </div>
                </Col>
                <Col xs={24} lg={8}>
                  <div className="ci-model-mini-chart">
                    <Text strong>成本</Text>
                    <ReactECharts
                      option={buildMiniLineOption('成本', '#d97706', dates, costValues, formatCost)}
                      style={{ height: 150 }}
                    />
                  </div>
                </Col>
              </Row>
            </section>
          </Space>
        </Spin>
      )}
    </Drawer>
  );
};

export default ModelMetricsDrawer;
