import React, { useCallback, useEffect, useState } from 'react';
import { Badge, Card, Empty, List, Progress, Space, Tag, Typography } from 'antd';
import {
  CloudUploadOutlined,
  DollarOutlined,
  FileDoneOutlined,
  PlayCircleOutlined,
  ProjectOutlined,
  RightOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Link } from 'react-router-dom';
import ReactECharts from 'echarts-for-react';
import { listSystems } from '../../api/system';
import { listTasks } from '../../api/task';
import { getTokenStats } from '../../api/token';
import type { TokenStats } from '../../api/token';
import { listVersions } from '../../api/knowledge';
import type { KnowledgeVersion } from '../../api/knowledge';
import type { PageResult, System, Task } from '../../types';

const { Text } = Typography;

// 运行中状态定义：这些状态下的任务被视为活跃中的分析任务
const runningStatuses = ['PENDING', 'PULLING_CODE', 'PARSING_CODE', 'SPLITTING_TASK', 'AI_ANALYZING', 'GENERATING_DOC'];

// 分页查询空值兜底模板
const emptyPage = <T,>(): PageResult<T> => ({
  total: 0,
  size: 0,
  current: 1,
  records: [],
});

// 审计统计指标空值兜底模板
const emptyTokenStats: TokenStats = {
  totalInputTokens: 0,
  totalOutputTokens: 0,
  totalTokens: 0,
  totalCost: 0,
  systemRanking: [],
  modelRatio: [],
  dailyTrends: [],
};

// 任务各阶段状态渲染元数据
const statusMeta: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PENDING: { color: 'blue', label: '排队中' },
  PULLING_CODE: { color: 'blue', label: '拉取代码' },
  PARSING_CODE: { color: 'cyan', label: '解析代码' },
  SPLITTING_TASK: { color: 'purple', label: '任务切片' },
  AI_ANALYZING: { color: 'gold', label: 'AI 分析中' },
  GENERATING_DOC: { color: 'orange', label: '生成文档' },
  PENDING_REVIEW: { color: 'magenta', label: '待复核' },
  REVIEWING: { color: 'geekblue', label: '复核中' },
  CONFIRMED: { color: 'green', label: '已确认' },
  PUSHING: { color: 'purple', label: '推送中' },
  PUSHED: { color: 'green', label: '已推送' },
  FAILED: { color: 'red', label: '失败' },
  CANCELLED: { color: 'default', label: '已取消' },
};

// 知识库推送版本状态说明映射
const versionStatusLabel: Record<string, string> = {
  DRAFT: '待推送',
  PUSHING: '推送中',
  PUSHED: '已推送',
  FAILED: '失败',
};

/**
 * 仪表盘看板页面组件 (Dashboard)
 * 呈现整个系统的全局 KPI 指标卡片（系统数、运行中任务数、待复核队列、Token总开销），
 * 并配置折线图展现 Token 每日消耗趋势、环形图呈现大模型占比，以及呈现最近的任务和推送状态。
 */
const Dashboard: React.FC = () => {
  // 看板核心 KPI 数据状态
  const [stats, setStats] = useState({
    systems: 0,
    activeTasks: 0,
    pendingReviews: 0,
    todayTokens: 0,
    cost: 0,
  });
  
  // 最近反编译任务列表
  const [recentTasks, setRecentTasks] = useState<Task[]>([]);
  // 最近推送记录列表
  const [recentPushes, setRecentPushes] = useState<KnowledgeVersion[]>([]);
  // Token 审计图表数据载荷
  const [chartData, setChartData] = useState<TokenStats | null>(null);
  // 全局加载状态
  const [loading, setLoading] = useState(false);

  /**
   * 异步加载工作台所需的所有业务数据
   * 采用 Promise.allSettled 策略进行并发抓取，即使部分 API 发生网络异常，也能确保其他板块数据正常渲染。
   */
  const fetchDashboardData = useCallback(async () => {
    setLoading(true);
    try {
      const [systemResult, taskResult, pushResult, tokenResult] = await Promise.allSettled([
        listSystems({ current: 1, size: 100 }),
        listTasks({ current: 1, size: 6 }),
        listVersions({ current: 1, size: 5 }),
        getTokenStats(),
      ]);
      
      // 提取成功或返回兜底的空实体
      const systemPage = systemResult.status === 'fulfilled' ? systemResult.value : emptyPage<System>();
      const taskPage = taskResult.status === 'fulfilled' ? taskResult.value : emptyPage<Task>();
      const pushPage = pushResult.status === 'fulfilled' ? pushResult.value : emptyPage<KnowledgeVersion>();
      const tokenStats = tokenResult.status === 'fulfilled' ? tokenResult.value : emptyTokenStats;

      // 聚合及提炼看板顶部的 KPI 数值指标
      setStats({
        systems: systemPage.total,
        // 过滤正在执行管道任务的条数
        activeTasks: taskPage.records.filter((task) => runningStatuses.includes(task.status)).length,
        // 过滤处于待复核状态的任务
        pendingReviews: taskPage.records.filter((task) => task.status === 'PENDING_REVIEW').length,
        todayTokens: tokenStats.totalTokens,
        cost: tokenStats.totalCost,
      });
      setRecentTasks(taskPage.records);
      setRecentPushes(pushPage.records);
      setChartData(tokenStats);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  // ECharts 配置：Token 每日消耗趋势折线图参数对象
  const lineOption = {
    color: ['#2563eb'],
    tooltip: { trigger: 'axis' },
    grid: { top: 28, right: 22, bottom: 30, left: 48 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: chartData?.dailyTrends?.map((item) => item.date) ?? [],
      axisLine: { lineStyle: { color: '#dbe4ef' } },
      axisLabel: { color: '#667085' },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#667085' },
      splitLine: { lineStyle: { color: '#edf2f7' } },
    },
    series: [
      {
        name: 'Token',
        type: 'line',
        data: chartData?.dailyTrends?.map((item) => item.tokens) ?? [],
        smooth: true,
        symbolSize: 7,
        lineStyle: { width: 3 },
        areaStyle: { color: 'rgba(37, 99, 235, 0.10)' },
      },
    ],
  };

  // ECharts 配置：各型号大模型 Token 占比环形图参数对象
  const donutOption = {
    color: ['#2563eb', '#0891b2', '#16a34a', '#d97706', '#dc2626'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, icon: 'circle', textStyle: { color: '#667085' } },
    series: [
      {
        name: '模型 Token',
        type: 'pie',
        radius: ['54%', '72%'],
        center: ['50%', '43%'],
        itemStyle: { borderColor: '#fff', borderWidth: 3 },
        label: { show: false },
        data: chartData?.modelRatio ?? [],
      },
    ],
  };

  // 生成 sparkline 7 天数据(实际接入时由 API 提供)
  const sparkSeries = (seed: number, base: number) =>
    Array.from({ length: 7 }, (_, i) =>
      Math.max(0, Math.round(base + Math.sin((i + seed) * 0.7) * (base * 0.18 + 1))),
    );

  // 简化 sparkline 配置:无坐标轴、无 tooltip,只画一条平滑线
  const buildSparkOption = (color: string, data: number[]) => ({
    color: [color],
    grid: { top: 2, right: 2, bottom: 2, left: 2 },
    xAxis: { type: 'category', show: false, data: data.map((_, i) => i) },
    yAxis: { type: 'value', show: false, scale: true },
    tooltip: { show: false },
    series: [
      {
        type: 'line',
        data,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1.5 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: color + '33' },
              { offset: 1, color: color + '00' },
            ],
          },
        },
      },
    ],
  });

  // KPI 顶部卡片元配置数组
  const kpis = [
    {
      label: '已接入系统',
      value: stats.systems,
      meta: '已配置的业务系统',
      change: '+2',
      changeUp: true,
      icon: <ProjectOutlined />,
      accent: '#3F74D8',
      sparkData: sparkSeries(1, Math.max(stats.systems, 4)),
    },
    {
      label: '运行中任务',
      value: stats.activeTasks,
      meta: '正在拉取 / 解析 / 生成',
      change: stats.activeTasks > 0 ? '运行中' : '空闲',
      changeUp: stats.activeTasks > 0,
      icon: <PlayCircleOutlined />,
      accent: '#7C5CD8',
      sparkData: sparkSeries(2, Math.max(stats.activeTasks, 3)),
    },
    {
      label: '复核队列',
      value: stats.pendingReviews,
      meta: '等待负责人复核的草稿',
      change: stats.pendingReviews > 0 ? `待处理 ${stats.pendingReviews}` : '已清空',
      changeUp: stats.pendingReviews > 0,
      icon: <FileDoneOutlined />,
      accent: '#D89A2F',
      sparkData: sparkSeries(3, Math.max(stats.pendingReviews, 2)),
    },
    {
      label: 'Token 用量',
      value: stats.todayTokens.toLocaleString(),
      meta: `预计 $${Number(stats.cost || 0).toFixed(4)}`,
      change: '本周',
      changeUp: true,
      icon: <DollarOutlined />,
      accent: '#10B981',
      sparkData: chartData?.dailyTrends?.slice(-7).map((d) => d.tokens) ?? sparkSeries(4, 1000),
    },
  ];

  return (
    <div className="ci-page ci-dashboard-page">
      {/* 头部介绍横幅 banner 区域 */}
      <section className="ci-hero-panel">
        <div className="ci-hero-content">
          <Space size={8} wrap>
            <Tag style={{ background: 'rgba(37, 99, 235, 0.15)', border: '1px solid rgba(37, 99, 235, 0.3)', color: '#60a5fa' }}>MVP 流程</Tag>
            <Tag style={{ background: 'rgba(22, 163, 74, 0.15)', border: '1px solid rgba(22, 163, 74, 0.3)', color: '#4ade80' }}>人工复核</Tag>
            <Tag style={{ background: 'rgba(8, 145, 178, 0.15)', border: '1px solid rgba(8, 145, 178, 0.3)', color: '#22d3ee' }}>Token 审计</Tag>
          </Space>
          <h2>把代码库转化为可复核、可追溯的代码知识资产</h2>
          <p>
            代码洞察工作台覆盖系统接入、代码库扫描、AI 自动归类、Markdown 草稿复核、版本发布、推送 Git 仓库、Token 计量和操作审计等全流程。
          </p>
        </div>
        
        {/* 快捷菜单入口操作板 */}
        <div className="ci-hero-actions-panel">
          <div className="ci-hero-actions-title">
            <ThunderboltOutlined style={{ color: '#38bdf8' }} /> 快捷操作
          </div>
          <Link to="/tasks" className="ci-hero-action-btn">
            <span>新建反编译分析任务</span>
            <RightOutlined />
          </Link>
          <Link to="/systems" className="ci-hero-action-btn">
            <span>接入新系统与代码库</span>
            <RightOutlined />
          </Link>
          <Link to="/drafts" className="ci-hero-action-btn">
            <span>进入草稿区进行复核</span>
            <RightOutlined />
          </Link>
        </div>
      </section>

      {/* KPI 卡片栏网格 */}
      <div className="ci-kpi-grid">
        {kpis.map((item) => (
          <div className="ci-kpi-card" style={{ '--accent': item.accent } as React.CSSProperties} key={item.label}>
            <div className="ci-kpi-card-header">
              <span className="ci-kpi-label">{item.label}</span>
              <span className="ci-kpi-icon-wrapper">{item.icon}</span>
            </div>
            <div className="ci-kpi-card-body">
              <div className="ci-kpi-value">{item.value}</div>
              <div className="ci-kpi-meta">
                <span className="ci-kpi-meta-main">{item.meta}</span>
                <span className={`ci-kpi-meta-change ${item.changeUp ? 'is-up' : 'is-neutral'}`}>
                  {item.change}
                </span>
              </div>
            </div>
            {item.sparkData && item.sparkData.length > 0 && (
              <div className="ci-kpi-spark">
                <ReactECharts
                  option={buildSparkOption(item.accent, item.sparkData)}
                  style={{ height: '100%' }}
                  opts={{ renderer: 'svg' }}
                />
              </div>
            )}
          </div>
        ))}
      </div>

      {/* 核心仪表盘图表与最近记录布局 */}
      <div className="ci-dashboard-layout">
        {/* 左侧：Token 趋势 与 最近任务 */}
        <div className="ci-dashboard-main">
          <Card 
            title={<span className="ci-card-title-gradient">Token 使用趋势</span>} 
            extra={<Link to="/audit" className="ci-card-extra-link">审计详情 <RightOutlined style={{ fontSize: 10 }} /></Link>}
            className="ci-dashboard-card"
          >
            {chartData ? <ReactECharts option={lineOption} style={{ height: 300 }} /> : <Empty />}
          </Card>

          <Card
            title={<Space><ThunderboltOutlined className="ci-icon-primary" />最近反编译任务</Space>}
            extra={<Link to="/tasks" className="ci-card-extra-link">查看全部 <RightOutlined style={{ fontSize: 10 }} /></Link>}
            className="ci-dashboard-card"
          >
            <List
              loading={loading}
              dataSource={recentTasks}
              locale={{ emptyText: <Empty description="暂无任务" /> }}
              renderItem={(task) => {
                const meta = statusMeta[task.status] ?? { color: 'default', label: task.status };
                return (
                  <List.Item
                    className="ci-task-list-item"
                    actions={[
                      <Link to={`/tasks/${task.id}`} key="detail" className="ci-card-extra-link">
                        详情 <RightOutlined style={{ fontSize: 10 }} />
                      </Link>
                    ]}
                  >
                    <div style={{ width: '100%' }}>
                      <div className="ci-task-title-row">
                        <Space wrap>
                          <Text strong style={{ fontSize: 15 }}>任务 #{task.id}</Text>
                          <Tag color={meta.color}>{meta.label}</Tag>
                          <Tag color={task.type === 'INITIAL' ? 'blue' : 'cyan'}>
                            {task.type === 'INITIAL' ? '全量' : '增量'}
                          </Tag>
                        </Space>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          耗时 {(task.durationMs / 1000 || 0).toFixed(1)} 秒
                        </Text>
                      </div>
                      <div className="ci-task-meta-row">
                        <div className="ci-task-progress-wrapper">
                          <Progress
                            percent={task.progress}
                            size="small"
                            status={task.status === 'FAILED' ? 'exception' : task.status === 'CONFIRMED' || task.status === 'PUSHED' ? 'success' : 'active'}
                            strokeColor={task.status === 'FAILED' ? undefined : {
                              '0%': '#2563eb',
                              '100%': '#0891b2',
                            }}
                          />
                        </div>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          进度: {task.progress}%
                        </Text>
                      </div>
                    </div>
                  </List.Item>
                );
              }}
            />
          </Card>
        </div>

        {/* 右侧：模型占比 与 最近推送 */}
        <div className="ci-dashboard-side">
          <Card 
            title={<span className="ci-card-title-gradient">模型使用占比</span>}
            className="ci-dashboard-card"
          >
            {chartData ? <ReactECharts option={donutOption} style={{ height: 300 }} /> : <Empty />}
          </Card>

          <Card
            title={<Space><CloudUploadOutlined className="ci-icon-success" />最近知识推送</Space>}
            extra={<Link to="/push" className="ci-card-extra-link">推送中心 <RightOutlined style={{ fontSize: 10 }} /></Link>}
            className="ci-dashboard-card"
          >
            <List
              loading={loading}
              dataSource={recentPushes}
              locale={{ emptyText: <Empty description="暂无推送记录" /> }}
              renderItem={(version) => {
                const statusColor = version.status === 'PUSHED' ? 'success' : version.status === 'FAILED' ? 'error' : 'processing';
                const statusLabel = versionStatusLabel[version.status] ?? version.status;
                return (
                  <List.Item className="ci-push-list-item">
                    <div style={{ width: '100%' }}>
                      <div className="ci-push-title-row">
                        <Space>
                          <Text strong style={{ fontSize: 15, fontFamily: 'JetBrains Mono' }}>{version.versionNum}</Text>
                          <Badge status={statusColor} text={statusLabel} />
                        </Space>
                        <Tag color="purple">{version.targetBranch}</Tag>
                      </div>
                      <div className="ci-push-detail-row">
                        <Space size={16}>
                          <Text type="secondary">
                            Commit: <span style={{ fontFamily: 'JetBrains Mono' }}>{version.sourceCommit?.slice(0, 8) || '-'}</span>
                          </Text>
                          <Text type="secondary">负责人: {version.confirmedBy || '未分配'}</Text>
                        </Space>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {version.confirmedAt ? new Date(version.confirmedAt).toLocaleDateString() : version.createdAt ? new Date(version.createdAt).toLocaleDateString() : ''}
                        </Text>
                      </div>
                    </div>
                  </List.Item>
                );
              }}
            />
          </Card>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;

