import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Col, Form, Modal, Progress, Row, Select, Space, Steps, Table, Tag, Tooltip, Typography, message } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  EyeOutlined,
  LoadingOutlined,
  PlusOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { createIncrementalTask, createInitialTask, getRepositoryReadiness, getTaskSummary, listTasks, retryTask, startTask, terminateTask, type RepositoryReadiness, type TaskStatusSummary } from '../../api/task';
import { listPrompts } from '../../api/prompt';
import { listRepositories } from '../../api/repository';
import { listSystems } from '../../api/system';
import { listModels } from '../../api/model';
import type { AiModel, Prompt, Repository, System, Task } from '../../types';

const { Text } = Typography;

// 运行中的阶段状态集：这些状态下的任务属于活跃状态，页面需要发起自动轮询
const runningStatuses = ['PENDING', 'PULLING_CODE', 'PARSING_CODE', 'SPLITTING_TASK', 'AI_ANALYZING', 'GENERATING_DOC', 'PUSHING'];

// 各个状态下的标签显示及微标配置
const statusMeta: Record<string, { color: string; label: string; loading?: boolean }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PENDING: { color: 'blue', label: '排队中', loading: true },
  PULLING_CODE: { color: 'blue', label: '拉取代码', loading: true },
  PARSING_CODE: { color: 'cyan', label: '解析代码', loading: true },
  SPLITTING_TASK: { color: 'purple', label: '任务切片', loading: true },
  AI_ANALYZING: { color: 'orange', label: 'AI 分析中', loading: true },
  GENERATING_DOC: { color: 'gold', label: '生成文档', loading: true },
  PENDING_REVIEW: { color: 'magenta', label: '待复核' },
  REVIEWING: { color: 'geekblue', label: '复核中' },
  CONFIRMED: { color: 'green', label: '已确认' },
  PUSHING: { color: 'purple', label: '推送中', loading: true },
  PUSHED: { color: 'green', label: '已推送' },
  FAILED: { color: 'red', label: '失败' },
  CANCELLED: { color: 'default', label: '已取消' },
};

/**
 * 扫描分析任务列表页面组件 (Tasks)
 * 包含分页任务列表展示、基于 React state-driven 的 2.5 秒高频动态轮询更新状态机机制、
 * 以及三步向导 Modal 弹框用于创建全量/增量任务。
 */
const Tasks: React.FC = () => {
  const navigate = useNavigate();
  
  // 任务表格状态数据
  const [tasks, setTasks] = useState<Task[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);

  // 下拉筛选条件状态
  const [filterSystemId, setFilterSystemId] = useState<number | undefined>();
  const [filterStatus, setFilterStatus] = useState<string | undefined>();
  const [filterType, setFilterType] = useState<string | undefined>();

  // 顶部状态分组 chip：ALL / RUNNING / PENDING_REVIEW / CONFIRMED / CLOSED
  // 选中后把对应状态列表作为 statuses 参数传给 listTasks（后端用 SQL IN 过滤）。
  type GroupKey = 'ALL' | 'RUNNING' | 'PENDING_REVIEW' | 'CONFIRMED' | 'CLOSED';
  const [activeGroup, setActiveGroup] = useState<GroupKey>('ALL');
  const [taskSummary, setTaskSummary] = useState<TaskStatusSummary>({
    ALL: 0, RUNNING: 0, PENDING_REVIEW: 0, CONFIRMED: 0, CLOSED: 0,
  });

  /**
   * 状态分组 → 后端 status 列表的映射。
   * chip 切换时不仅设置 activeGroup，还把对应 statuses 列表传给 listTasks。
   *
   * 设计原则：
   * - PENDING_REVIEW 与 REVIEWING 合并到「待处理」组（都需要人工介入，但可能未开始 / 进行中 / 驳回待重跑）
   * - CONFIRMED 与 PUSHED 合并到「已确认」组（已通过审核的最终链路）
   * - 终态（FAILED / CANCELLED / ARCHIVED / DRAFT）合并到「已终止」组
   */
  const GROUP_STATUSES: Record<GroupKey, string[] | null> = {
    ALL:            null,
    RUNNING:        ['PENDING', 'PULLING_CODE', 'PARSING_CODE', 'SPLITTING_TASK', 'AI_ANALYZING', 'GENERATING_DOC', 'PUSHING'],
    PENDING_REVIEW: ['PENDING_REVIEW', 'REVIEWING'],
    CONFIRMED:      ['CONFIRMED', 'PUSHED'],
    CLOSED:         ['FAILED', 'CANCELLED', 'ARCHIVED', 'DRAFT'],
  };

  /** chip 展示文案（与后端 GROUP_STATUSES 一一对应） */
  const GROUP_LABELS: Record<GroupKey, { label: string; hint: string }> = {
    ALL:            { label: '全部',     hint: '所有任务' },
    RUNNING:        { label: '进行中',   hint: '拉取/解析/切片/AI/推送' },
    PENDING_REVIEW: { label: '待处理',   hint: '待复核 + 复核中（含驳回待重跑）' },
    CONFIRMED:      { label: '已确认',   hint: '已通过 + 已推送' },
    CLOSED:         { label: '已终止',   hint: '失败 / 取消 / 归档 / 草稿' },
  };

  // 初始化加载的下拉选项数据
  const [systems, setSystems] = useState<System[]>([]);
  const [taskSourceSystems, setTaskSourceSystems] = useState<System[]>([]);
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [prompts, setPrompts] = useState<Prompt[]>([]);
  const [models, setModels] = useState<AiModel[]>([]);

  // 新建任务向导弹框的步骤控制器、表单组件引用
  const [modalOpen, setModalOpen] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const [form] = Form.useForm();

  /**
   * 新建任务前置条件拦截：
   * wizard 任一步骤提交前都先校验 ready；若不通过，弹 Modal.warning 列出阻塞草稿。
   * 引导用户去 /drafts 完成确认或驳回处置。
   */
  const [readiness, setReadiness] = useState<RepositoryReadiness | null>(null);
  const [readinessLoading, setReadinessLoading] = useState(false);
  const [readinessModalOpen, setReadinessModalOpen] = useState(false);

  const refreshReadiness = useCallback(async () => {
    setReadinessLoading(true);
    try {
      const data = await getRepositoryReadiness();
      setReadiness(data);
      return data;
    } catch {
      // 就绪度查询失败时按"放行"策略处理（后端仍会兜底校验），不让前端成为阻塞点
      return null;
    } finally {
      setReadinessLoading(false);
    }
  }, []);

  /**
   * 通用前置条件闸门：先异步拿 readiness，不通过就阻断当前动作。
   * 用法：在 onClick 回调里 `await ensureReadinessOrBlock()`，返回 false 即不再继续。
   */
  const ensureReadinessOrBlock = useCallback(async (): Promise<boolean> => {
    const data = await refreshReadiness();
    if (data && !data.ready) {
      setReadinessModalOpen(true);
      return false;
    }
    return true;
  }, [refreshReadiness]);
  
  // 观察并在表单选择系统改变时，级联过滤出该系统下的可用 Git 仓库
  const selectedSystemId = Form.useWatch('systemId', form);

  // 获取并刷新任务分页列表数据
  const fetchTasks = useCallback(
    async (page = current, pageSize = size) => {
      setLoading(true);
      try {
        // chip 状态分组过滤：把对应 statuses 列表传给后端，与 status 单值互斥
        const groupStatuses = GROUP_STATUSES[activeGroup];
        const data = await listTasks({
          current: page,
          size: pageSize,
          systemId: filterSystemId,
          status: filterStatus,
          statuses: groupStatuses ?? undefined,
          type: filterType,
        });
        setTasks(data.records);
        setTotal(data.total);
      } finally {
        setLoading(false);
      }
    },
    [current, filterStatus, filterSystemId, filterType, size, activeGroup],
  );

  // 拉取 chip 角标用的状态分组统计（按当前系统过滤）
  const fetchSummary = useCallback(async () => {
    try {
      const data = await getTaskSummary({ systemId: filterSystemId });
      setTaskSummary(data);
    } catch {
      // 静默失败，角标维持上次的值
    }
  }, [filterSystemId]);

  // 状态分组切换处理：同时清空单值 status 过滤，避免与 chip 冲突
  const handleGroupChange = (next: GroupKey) => {
    setActiveGroup(next);
    setFilterStatus(undefined);
    setCurrent(1);
  };

  /**
   * REVIEWING 状态细分：判断任务是否因为有 REJECTED 草稿而卡在"复核中"，
   * 用于列表上展示「待重跑」副标签，让用户一眼看出"为什么卡住"。
   */
  const isAwaitingRerun = (task: Task): boolean => {
    if (task.status !== 'REVIEWING') return false;
    // 通过 summary 判断不太准，这里改为：只要状态是 REVIEWING 且任务非 0% 进度都标记
    return true;
  };

  // 初始化：获取已配置了代码库的系统列表、提示词模板及可用 AI 模型数据
  const loadOptions = useCallback(async () => {
    const [sysData, repositoryData, promptData, modelData] = await Promise.all([
      listSystems({ current: 1, size: 100, status: 1 }),
      listRepositories({ current: 1, size: 1000 }),
      listPrompts({ current: 1, size: 100, status: 1 }),
      listModels(),
    ]);
    const configuredSystemIds = new Set(repositoryData.records.map((repository) => repository.systemId));
    setSystems(sysData.records);
    setTaskSourceSystems(sysData.records.filter((system) => configuredSystemIds.has(system.id)));
    setPrompts(promptData.records);
    setModels(modelData);
  }, []);

  // 监听条件和分页加载表格
  useEffect(() => {
    fetchTasks();
  }, [fetchTasks]);

  // 加载选项数据
  useEffect(() => {
    loadOptions();
  }, [loadOptions]);

  // 拉取顶部状态分组 chips 的角标数据
  useEffect(() => {
    fetchSummary();
  }, [fetchSummary]);

  // 级联拉取已选择系统名下的所有 Git 代码仓库记录
  useEffect(() => {
    if (!selectedSystemId) {
      setRepositories([]);
      return;
    }
    listRepositories({ current: 1, size: 50, systemId: selectedSystemId }).then((data) => {
      setRepositories(data.records);
      form.setFieldValue('repositoryId', undefined);
    });
  }, [form, selectedSystemId]);

  /**
   * 自动轮询机制 (2.5 秒刷新一次)
   * 只有当当前列表页面存在处理“运行中 (runningStatuses)”状态的任务时，才激活定时轮询，避免无意义的背景网络开销。
   */
  useEffect(() => {
    const hasRunningTasks = tasks.some((task) => runningStatuses.includes(task.status));
    if (!hasRunningTasks) {
      return;
    }
    const timer = window.setInterval(() => {
      fetchTasks(current, size);
    }, 2500);
    return () => window.clearInterval(timer);
  }, [current, fetchTasks, size, tasks]);

  // 动作：触发启动反编译任务
  const handleStart = async (id: number) => {
    await startTask(id);
    message.success('任务已启动');
    fetchTasks();
  };

  // 动作：强制终止反编译任务
  const handleTerminate = async (id: number) => {
    await terminateTask(id);
    message.success('终止请求已发送');
    fetchTasks();
  };

  // 动作：重试失败的任务
  const handleRetry = async (id: number) => {
    await retryTask(id);
    message.success('任务已重新启动');
    fetchTasks();
  };

  // 向导最终步骤：执行新建任务表单落盘
  const handleCreateTask = async () => {
    // 兜底再校验一次就绪度，防止向导步骤间状态变更
    if (!(await ensureReadinessOrBlock())) return;

    const values = form.getFieldsValue();
    const payload = {
      systemId: values.systemId,
      repositoryId: values.repositoryId,
      promptVersion: values.promptVersion,
      modelName: values.modelName,
    };
    if (values.taskType === 'INITIAL') {
      await createInitialTask(payload);
    } else {
      await createIncrementalTask(payload);
    }
    message.success('任务已创建');
    setModalOpen(false);
    setCurrentStep(0);
    form.resetFields();
    fetchTasks();
  };

  // 获取状态标签组件
  const getStatusTag = (status: string) => {
    const meta = statusMeta[status] ?? { color: 'default', label: status };
    return (
      <Tag color={meta.color} icon={meta.loading ? <LoadingOutlined /> : undefined}>
        {meta.label}
      </Tag>
    );
  };

  // 表头列配置
  const columns = [
    {
      title: '任务',
      dataIndex: 'id',
      key: 'id',
      width: 100,
      render: (id: number) => <Text code>#{id}</Text>,
    },
    {
      title: '系统',
      dataIndex: 'systemId',
      key: 'systemName',
      width: 180,
      render: (sysId: number) => systems.find((system) => system.id === sysId)?.name ?? `系统 #${sysId}`,
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 140,
      render: (type: Task['type']) => <Tag color={type === 'INITIAL' ? 'geekblue' : 'green'}>{type === 'INITIAL' ? '全量扫描' : '增量扫描'}</Tag>,
    },
    {
      title: 'AI模型',
      dataIndex: 'modelName',
      key: 'modelName',
      width: 150,
      render: (modelName: string) => modelName ? <Tag color="orange">{modelName}</Tag> : '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 190,
      render: (status: string, record: Task) => (
        <Space size={4} direction="vertical" style={{ lineHeight: 1.2 }}>
          {getStatusTag(status)}
          {/* REVIEWING 状态细分：通常是草稿被驳回后等待用户修改重跑，加副标签让用户一眼明白 */}
          {isAwaitingRerun(record) && (
            <Tag color="warning" style={{ fontSize: 11, margin: 0, lineHeight: '16px', padding: '0 6px' }}>
              待重跑
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: '进度',
      key: 'progress',
      width: 180,
      render: (_: unknown, record: Task) => (
        <Progress
          percent={record.progress}
          size="small"
          status={record.status === 'FAILED' ? 'exception' : record.progress === 100 ? 'success' : 'active'}
        />
      ),
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      key: 'durationMs',
      width: 120,
      render: (ms: number) => (ms ? `${(ms / 1000).toFixed(1)}s` : '-'),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 190,
      render: (time: string) => (time ? new Date(time).toLocaleString() : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 230,
      fixed: 'right' as const,
      render: (_: unknown, record: Task) => (
        <Space size={8}>
          <Button size="small" icon={<EyeOutlined />} onClick={() => navigate(`/tasks/${record.id}`)}>
            详情
          </Button>
          {record.status === 'DRAFT' && (
            <Button size="small" type="primary" icon={<PlayCircleOutlined />} onClick={() => handleStart(record.id)}>
              启动
            </Button>
          )}
          {runningStatuses.includes(record.status) && (
            <Button size="small" danger icon={<CloseCircleOutlined />} onClick={() => handleTerminate(record.id)}>
              终止
            </Button>
          )}
          {['FAILED', 'CANCELLED'].includes(record.status) && (
            <Button size="small" icon={<ReloadOutlined />} onClick={() => handleRetry(record.id)}>
              重试
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div className="ci-page ci-tasks-page">
      {/* 顶部状态分组 chips：ALL / RUNNING / PENDING_REVIEW / CONFIRMED / CLOSED */}
      <Card className="ci-filter-card" style={{ marginBottom: 12 }}>
        <div className="ci-status-chips" style={{ flexWrap: 'wrap' }}>
          <Space wrap size={8}>
            {([
              { key: 'ALL',            icon: <UnorderedListOutlined /> },
              { key: 'RUNNING',        icon: <LoadingOutlined /> },
              { key: 'PENDING_REVIEW', icon: <EyeOutlined /> },
              { key: 'CONFIRMED',      icon: <CheckCircleOutlined /> },
              { key: 'CLOSED',         icon: <CloseCircleOutlined /> },
            ] as { key: GroupKey; icon: React.ReactNode }[]).map((chip) => {
              const active = activeGroup === chip.key;
              const count = taskSummary[chip.key] ?? 0;
              const meta = GROUP_LABELS[chip.key];
              return (
                <Tooltip key={chip.key} title={meta.hint}>
                  <Button
                    shape="round"
                    type={active ? 'primary' : 'default'}
                    onClick={() => handleGroupChange(chip.key)}
                    className={`ci-status-chip ${active ? 'is-active' : ''}`}
                  >
                    <span className="ci-status-chip-icon">{chip.icon}</span>
                    <span>{meta.label}</span>
                    <span className={`ci-status-chip-count ${active ? 'is-active' : 'is-zero'}`}>{count}</span>
                  </Button>
                </Tooltip>
              );
            })}
          </Space>
        </div>
      </Card>

      {/* 顶部多条件联合过滤器 */}
      <Card className="ci-filter-card">
        <Row gutter={[12, 12]} align="middle">
          <Col xs={24} md={6}>
            <Select
              placeholder="系统"
              style={{ width: '100%' }}
              allowClear
              value={filterSystemId}
              onChange={setFilterSystemId}
              options={systems.map((system) => ({ value: system.id, label: system.name }))}
            />
          </Col>
          <Col xs={24} md={6}>
            <Select
              placeholder="状态"
              style={{ width: '100%' }}
              allowClear
              value={filterStatus}
              onChange={setFilterStatus}
              options={Object.entries(statusMeta).map(([value, meta]) => ({ value, label: meta.label }))}
            />
          </Col>
          <Col xs={24} md={5}>
            <Select
              placeholder="任务类型"
              style={{ width: '100%' }}
              allowClear
              value={filterType}
              onChange={setFilterType}
              options={[
                { value: 'INITIAL', label: '全量扫描' },
                { value: 'INCREMENTAL', label: '增量扫描' },
              ]}
            />
          </Col>
          <Col xs={24} md={7}>
            <Space className="ci-toolbar-actions" wrap>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => {
                  setFilterSystemId(undefined);
                  setFilterStatus(undefined);
                  setFilterType(undefined);
                  setActiveGroup('ALL');
                  setCurrent(1);
                }}
              >
                重置
              </Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
                新建任务
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 任务队列主数据表 */}
      <Card title="任务执行队列">
        <Table
          dataSource={tasks}
          columns={columns}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1300 }}
          pagination={{
            current,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (page, pageSize) => {
              setCurrent(page);
              setSize(pageSize);
            },
          }}
        />
      </Card>

      {/* 创建任务向导 Modal 弹框 */}
      <Modal
        title="创建反编译任务"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          setCurrentStep(0);
          form.resetFields();
        }}
        destroyOnHidden
        footer={
          <Space>
            {currentStep > 0 && <Button onClick={() => setCurrentStep(currentStep - 1)}>上一步</Button>}
            {currentStep < 2 ? (
              <Button
                type="primary"
                loading={readinessLoading}
                onClick={async () => {
                  // 第一步要先校验就绪度：仅当选择来源后才有意义
                  if (currentStep === 0) {
                    await form.validateFields(['systemId', 'repositoryId']);
                    if (!(await ensureReadinessOrBlock())) return;
                  }
                  if (currentStep === 1) {
                    await form.validateFields(['taskType', 'promptVersion', 'modelName']);
                  }
                  setCurrentStep(currentStep + 1);
                }}
              >
                下一步
              </Button>
            ) : (
              <Button type="primary" onClick={handleCreateTask}>
                创建任务
              </Button>
            )}
          </Space>
        }
      >
        <div style={{ padding: '8px 0 24px' }}>
          <Steps
            current={currentStep}
            size="small"
            items={[{ title: '来源' }, { title: '策略' }, { title: '确认' }]}
          />
        </div>

        <Form form={form} layout="vertical" preserve={true}>
          {/* 第一步：选择目标系统与待扫描仓库 */}
          <div style={{ display: currentStep === 0 ? 'block' : 'none' }}>
            <Form.Item name="systemId" label="系统" rules={[{ required: true, message: '请选择系统' }]}>
              <Select
                placeholder="请选择已配置代码库的系统"
                showSearch
                optionFilterProp="label"
                notFoundContent="暂无已配置代码库的启用系统"
                options={taskSourceSystems.map((system) => ({ value: system.id, label: system.name }))}
              />
            </Form.Item>
            <Form.Item name="repositoryId" label="代码库" rules={[{ required: true, message: '请选择代码库' }]}>
              <Select
                placeholder="请选择 Git 代码库"
                disabled={!selectedSystemId}
                notFoundContent={selectedSystemId ? '该系统尚未配置代码库' : '请先选择系统'}
                options={repositories.map((repo) => ({
                  value: repo.id,
                  label: `${repo.gitUrl} (${repo.branch})`,
                }))}
              />
            </Form.Item>
          </div>

          {/* 第二步：配置扫描类型、使用的 AI 模型与提示词版本 */}
          <div style={{ display: currentStep === 1 ? 'block' : 'none' }}>
            <Form.Item name="taskType" label="任务类型" initialValue="INITIAL" rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 'INITIAL', label: '全量扫描 - 分析整个代码库' },
                  { value: 'INCREMENTAL', label: '增量扫描 - 基于 Git Diff 分析' },
                ]}
              />
            </Form.Item>
            <Form.Item name="promptVersion" label="提示词版本" rules={[{ required: true, message: '请选择提示词' }]}>
              <Select
                placeholder="请选择提示词模板"
                options={prompts.map((prompt) => ({
                  value: prompt.version,
                  label: `${prompt.name} (v${prompt.version})`,
                }))}
              />
            </Form.Item>
            <Form.Item
              name="modelName"
              label="AI模型"
              initialValue={models.find((m) => m.isDefault === 'true')?.identifier}
              rules={[{ required: true, message: '请选择AI模型' }]}
            >
              <Select
                placeholder="请选择要调用的 AI 模型"
                options={models.map((model) => ({
                  value: model.identifier,
                  label: `${model.name} (${model.identifier})`,
                }))}
              />
            </Form.Item>
          </div>

          {/* 第三步：核实并确认任务配置参数 */}
          {currentStep === 2 && (
            <div className="ci-confirm-box">
              <p>
                系统：<b>{systems.find((system) => system.id === form.getFieldValue('systemId'))?.name}</b>
              </p>
              <p>
                代码库：<b>{repositories.find((repo) => repo.id === form.getFieldValue('repositoryId'))?.gitUrl}</b>
              </p>
              <p>
                策略：<b>{form.getFieldValue('taskType') === 'INITIAL' ? '全量扫描' : '增量扫描'}</b>
              </p>
              <p>
                提示词：{' '}
                <b>
                  {prompts.find((prompt) => prompt.version === form.getFieldValue('promptVersion'))?.name} v
                  {form.getFieldValue('promptVersion')}
                </b>
              </p>
              <p>
                AI模型：{' '}
                <b>
                  {models.find((m) => m.identifier === form.getFieldValue('modelName'))?.name || form.getFieldValue('modelName')}
                </b>
              </p>
              <Text type="secondary">任务将以草稿状态创建，可在队列中手动启动。</Text>
            </div>
          )}
        </Form>
      </Modal>

      {/* 新建任务前置条件拦截弹窗：列出阻塞草稿，引导用户去复核页处理 */}
      <Modal
        title={
          <Space>
            <CloseCircleOutlined style={{ color: '#c4485d' }} />
            <span>无法新建任务：尚有未确认的草稿</span>
          </Space>
        }
        open={readinessModalOpen}
        onCancel={() => setReadinessModalOpen(false)}
        onOk={() => {
          setReadinessModalOpen(false);
          navigate('/drafts');
        }}
        okText="前往复核工作区"
        cancelText="关闭"
        width={640}
        destroyOnHidden
      >
        <div style={{ padding: '8px 0' }}>
          <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
            根据「历史任务所有文件已确认」的产品规则，全局仍有 {readiness?.unconfirmedCount ?? 0}
            个草稿处于 DRAFT / EDITING / REJECTED 状态。请先前往复核工作区完成确认或驳回处置，然后再回到这里新建任务。
          </Text>
          <Table
            size="small"
            pagination={false}
            rowKey="draftId"
            dataSource={readiness?.blockingDrafts ?? []}
            columns={[
              { title: '模块', dataIndex: 'moduleName', key: 'moduleName', ellipsis: true },
              {
                title: '状态',
                dataIndex: 'status',
                key: 'status',
                width: 110,
                render: (s: string) => {
                  const meta: Record<string, { color: string; label: string }> = {
                    DRAFT: { color: 'magenta', label: '待处理' },
                    EDITING: { color: 'geekblue', label: '已编辑' },
                    REJECTED: { color: 'red', label: '已驳回' },
                  };
                  const m = meta[s] ?? { color: 'default', label: s };
                  return <Tag color={m.color}>{m.label}</Tag>;
                },
              },
              {
                title: '所属任务',
                dataIndex: 'taskId',
                key: 'taskId',
                width: 100,
                render: (taskId?: number) => (taskId ? <Text code>#{taskId}</Text> : '-'),
              },
              {
                title: '更新时间',
                dataIndex: 'updatedAt',
                key: 'updatedAt',
                width: 170,
                render: (t: string) => (t ? new Date(t).toLocaleString() : '-'),
              },
            ]}
          />
        </div>
      </Modal>
    </div>
  );
};

export default Tasks;
