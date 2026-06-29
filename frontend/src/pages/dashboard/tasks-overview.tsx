import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Skeleton, Space, Statistic, Tag, Typography } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { getTaskStats, type TaskStats } from '../../api/dashboard';

const { Text, Title } = Typography;

const TaskOverview: React.FC = () => {
  const [stats, setStats] = useState<TaskStats | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    getTaskStats(30).then(setStats).finally(() => setLoading(false));
  }, []);

  if (loading || !stats) return <Skeleton active paragraph={{ rows: 12 }} />;

  // ── 状态分布饼图 ──
  const statusPieOption = {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      avoidLabelOverlap: true,
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      data: Object.entries(stats.byStatus ?? {}).map(([k, v]) => ({ name: k, value: v })),
    }],
  };

  // ── 每日创建趋势折线图 ──
  const trendOption = {
    tooltip: { trigger: 'axis' },
    grid: { top: 28, right: 22, bottom: 30, left: 52 },
    xAxis: { type: 'category', data: Object.keys(stats.dailyCount ?? {}), axisLabel: { color: '#667085', fontSize: 10 } },
    yAxis: { type: 'value', axisLabel: { color: '#667085' } },
    series: [
      { name: '任务数', type: 'line', smooth: true, data: Object.values(stats.dailyCount ?? {}), color: '#2563eb', areaStyle: { opacity: 0.15 } },
    ],
  };

  // ── 系统分布柱状图 ──
  const sysBarOption = {
    tooltip: { trigger: 'axis' },
    grid: { top: 28, right: 22, bottom: 60, left: 60 },
    xAxis: { type: 'category', data: Object.keys(stats.bySystem ?? {}), axisLabel: { color: '#667085', rotate: 30, fontSize: 10 } },
    yAxis: { type: 'value', axisLabel: { color: '#667085' } },
    series: [{ type: 'bar', data: Object.values(stats.bySystem ?? {}), color: '#2563eb', barMaxWidth: 40 }],
  };

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>任务概览</Title>

      {/* 指标卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card><Statistic title="任务总数" value={stats.total} prefix={<BarChartOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card><Statistic title="已完成 (PUSHED)" value={stats.successCount} prefix={<CheckCircleOutlined />} valueStyle={{ color: '#16a34a' }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card><Statistic title="失败" value={stats.failedCount} prefix={<CloseCircleOutlined />} valueStyle={{ color: '#dc2626' }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card><Statistic title="平均耗时 (ms)" value={stats.avgDurationMs} prefix={<ClockCircleOutlined />} /></Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8}>
          <Card title="状态分布" size="small">
            <ReactECharts option={statusPieOption} style={{ height: 260 }} />
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="类型分布" size="small">
            <Space direction="vertical" style={{ width: '100%', padding: '16px 0' }}>
              {Object.entries(stats.byType ?? {}).map(([type, count]) => (
                <div key={type} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0' }}>
                  <Tag color={type === 'INITIAL' ? 'geekblue' : 'green'}>{type}</Tag>
                  <Text strong>{count}</Text>
                </div>
              ))}
            </Space>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="每日创建趋势（近 30 天）" size="small">
            <ReactECharts option={trendOption} style={{ height: 220 }} />
          </Card>
        </Col>
      </Row>

      <Card title="系统分布" size="small" style={{ marginTop: 16 }}>
        <ReactECharts option={sysBarOption} style={{ height: 260 }} />
      </Card>
    </div>
  );
};

export default TaskOverview;
