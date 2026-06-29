import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Row,
  Select,
  Space,
  Steps,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  ArrowLeftOutlined,
  CloseCircleOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  createIncrementalTask,
  createInitialTask,
  getRepositoryReadiness,
  type RepositoryReadiness,
} from '../../api/task';
import { listPrompts } from '../../api/prompt';
import { listRepositories } from '../../api/repository';
import { listSystems } from '../../api/system';
import { listModels } from '../../api/model';
import type {
  AiModel,
  EntryScanConfig,
  Prompt,
  Repository,
  System,
} from '../../types';

const { Text } = Typography;

const DEFAULT_EXCLUDE_CLASSPATHS = ['**/*Test', '**/*Tests', '**/*TestCase'];

const buildScanConfigWithDefaults = (
  config: EntryScanConfig | Record<string, unknown> | undefined,
): EntryScanConfig => {
  const base = (config || {}) as EntryScanConfig;
  return {
    ...base,
    excludeClasspaths:
      Array.isArray(base.excludeClasspaths) && base.excludeClasspaths.length > 0
        ? base.excludeClasspaths
        : DEFAULT_EXCLUDE_CLASSPATHS,
  };
};

const parseRepoEntryScanConfig = (repo: Repository | undefined) => {
  if (!repo?.entryScanConfig) return buildScanConfigWithDefaults(undefined);
  if (typeof repo.entryScanConfig === 'string') {
    try {
      return buildScanConfigWithDefaults(JSON.parse(repo.entryScanConfig));
    } catch {
      return buildScanConfigWithDefaults(undefined);
    }
  }
  return buildScanConfigWithDefaults(repo.entryScanConfig);
};

/**
 * 「手动下发」全屏页签
 *
 *  - 步骤 0：选择系统 + 代码库
 *  - 步骤 1：选择任务类型、AI 模型、扫描规则、是否启用模块层级调试
 *          （提示词由系统级绑定 + 默认提示词提供，任务不再单独选择）
 *  - 步骤 2：确认 + 提交
 *
 * 通过 ?systemId=&repositoryId= 预填来源（来自系统页"立即扫描"按钮）。
 *
 * 后端约束：系统必须处于 ACTIVE 状态；提示词由后端按
 *   任务显式 ID → 系统绑定 → 默认提示词 的顺序自动解析。
 */
const TaskDispatchPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [form] = Form.useForm();

  const [currentStep, setCurrentStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);

  // 下拉选项
  const [systems, setSystems] = useState<System[]>([]);
  const [taskSourceSystems, setTaskSourceSystems] = useState<System[]>([]);
  const [repositories, setRepositories] = useState<Repository[]>([]);
  /** 用于只读展示系统绑定的提示词（按需懒加载） */
  const [modularizeById, setModularizeById] = useState<Record<number, Prompt>>({});
  const [documentById, setDocumentById] = useState<Record<number, Prompt>>({});
  const [models, setModels] = useState<AiModel[]>([]);

  // 就绪度拦截
  const [readiness, setReadiness] = useState<RepositoryReadiness | null>(null);
  const [readinessLoading, setReadinessLoading] = useState(false);
  const [readinessModalOpen, setReadinessModalOpen] = useState(false);

  const selectedSystemId = Form.useWatch('systemId', form);
  const selectedRepositoryId = Form.useWatch('repositoryId', form);

  // 选中的系统对象（用于只读展示绑定的提示词）
  const selectedSystem: System | undefined = systems.find((s) => s.id === selectedSystemId);

  /** 把 system.modularizePromptId / documentPromptId 转成名称展示；未绑定的回退默认提示词 */
  const resolveBoundName = (
    boundId: number | null | undefined,
    fallbackId: number | null | undefined,
    map: Record<number, Prompt>,
  ): { name: string; isDefault: boolean; prompt?: Prompt } => {
    const id = boundId ?? fallbackId;
    if (id == null) return { name: '未绑定（运行时回退默认提示词）', isDefault: true };
    const p = map[id];
    if (p) return { name: `${p.name} (v${p.version})`, isDefault: false, prompt: p };
    return { name: `#${id}（详情待加载）`, isDefault: false };
  };

  // 拉系统/模型列表
  const loadOptions = useCallback(async () => {
    try {
      const [sysData, repositoryData, modelData] = await Promise.all([
        listSystems({ current: 1, size: 100 }),
        listRepositories({ current: 1, size: 1000 }),
        listModels(),
      ]);
      const configuredSystemIds = new Set(repositoryData.records.map((r) => r.systemId));
      setSystems(sysData.records);
      setTaskSourceSystems(sysData.records.filter((s) => configuredSystemIds.has(s.id)));
      setModels(modelData);
    } catch {
      // 拦截器已提示
    }
  }, []);

  // 选系统变化时，按需懒加载系统绑定的提示词 + 默认提示词
  useEffect(() => {
    if (!selectedSystem) return;
    const needModularize = selectedSystem.modularizePromptId;
    const needDocument = selectedSystem.documentPromptId;
    const fetchOne = async (type: 'MODULARIZE' | 'DOCUMENT_GENERATION', id?: number | null) => {
      if (id == null) return null;
      const all = await listPrompts({ current: 1, size: 200, status: 1, promptType: type });
      return all.records.find((p) => p.id === id) || null;
    };
    if (needModularize && !modularizeById[needModularize]) {
      fetchOne('MODULARIZE', needModularize).then((p) => {
        if (p) setModularizeById((m) => ({ ...m, [p.id]: p }));
      });
    }
    if (needDocument && !documentById[needDocument]) {
      fetchOne('DOCUMENT_GENERATION', needDocument).then((p) => {
        if (p) setDocumentById((m) => ({ ...m, [p.id]: p }));
      });
    }
  }, [selectedSystem, modularizeById, documentById]);

  const refreshReadiness = useCallback(async (systemId?: number, repositoryId?: number) => {
    setReadinessLoading(true);
    try {
      return await getRepositoryReadiness({ systemId, repositoryId });
    } catch {
      return null;
    } finally {
      setReadinessLoading(false);
    }
  }, []);

  const ensureReadinessOrBlock = useCallback(
    async (systemId?: number, repositoryId?: number): Promise<boolean> => {
      const data = await refreshReadiness(systemId, repositoryId);
      if (data) setReadiness(data);
      if (data && !data.ready) {
        setReadinessModalOpen(true);
        return false;
      }
      return true;
    },
    [refreshReadiness],
  );

  // 预填：?systemId=&repositoryId=
  useEffect(() => {
    const sysId = Number(searchParams.get('systemId'));
    const repoId = Number(searchParams.get('repositoryId'));
    if (Number.isFinite(sysId) && sysId > 0) {
      form.setFieldsValue({ systemId: sysId });
    }
    if (Number.isFinite(repoId) && repoId > 0) {
      form.setFieldsValue({ repositoryId: repoId });
    }
    if (searchParams.get('systemId') || searchParams.get('repositoryId')) {
      const next = new URLSearchParams(searchParams);
      next.delete('systemId');
      next.delete('repositoryId');
      setSearchParams(next, { replace: true });
    }
  }, []);

  useEffect(() => {
    loadOptions();
  }, [loadOptions]);

  // 系统变化 → 拉取仓库
  useEffect(() => {
    if (!selectedSystemId) {
      setRepositories([]);
      return;
    }
    listRepositories({ current: 1, size: 50, systemId: selectedSystemId }).then((data) => {
      setRepositories(data.records);
      const currentRepoId = form.getFieldValue('repositoryId');
      if (currentRepoId && !data.records.some((r) => r.id === currentRepoId)) {
        form.setFieldValue('repositoryId', undefined);
      }
    });
  }, [form, selectedSystemId]);

  // 仓库变化 → 用仓库默认配置初始化 entryScanConfig
  useEffect(() => {
    if (!selectedRepositoryId) {
      form.setFieldValue('entryScanConfig', buildScanConfigWithDefaults(undefined));
      return;
    }
    const repo = repositories.find((r) => r.id === selectedRepositoryId);
    form.setFieldValue('entryScanConfig', parseRepoEntryScanConfig(repo));
  }, [form, repositories, selectedRepositoryId]);

  const handleNext = async () => {
    if (currentStep === 0) {
      const vals = await form.validateFields(['systemId', 'repositoryId']);
      if (!(await ensureReadinessOrBlock(vals.systemId, vals.repositoryId))) return;
    } else if (currentStep === 1) {
      await form.validateFields(['taskType', 'modelName']);
    }
    setCurrentStep((s) => s + 1);
  };

  const handleSubmit = async () => {
    const vals = form.getFieldsValue(['systemId', 'repositoryId']);
    if (!(await ensureReadinessOrBlock(vals.systemId, vals.repositoryId))) return;

    setSubmitting(true);
    try {
      const values = form.getFieldsValue();
      const payload = {
        systemId: values.systemId,
        repositoryId: values.repositoryId,
        modelName: values.modelName,
        entryScanConfig: values.entryScanConfig,
        requireHierarchyReview: values.requireHierarchyReview !== false,
      };
      if (values.taskType === 'INITIAL') {
        await createInitialTask(payload);
      } else {
        await createIncrementalTask(payload);
      }
      message.success(
        values.requireHierarchyReview === false
          ? '任务已创建（跳过模块层级调试）'
          : '任务已创建（启用模块层级调试）',
      );
      navigate('/tasks/query');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSyncRepoScanConfig = () => {
    if (!selectedRepositoryId) {
      message.warning('请先选择仓库');
      return;
    }
    const repo = repositories.find((r) => r.id === selectedRepositoryId);
    if (!repo) {
      message.warning('未找到当前仓库');
      return;
    }
    form.setFieldsValue({ entryScanConfig: parseRepoEntryScanConfig(repo) });
    message.success('已恢复为仓库默认配置');
  };

  // 摘要：第三步确认
  const summarySystem = systems.find((s) => s.id === form.getFieldValue('systemId'))?.name;
  const summaryRepo = repositories.find((r) => r.id === form.getFieldValue('repositoryId'))?.gitUrl;
  const summaryTaskType = form.getFieldValue('taskType');
  const summaryModel = models.find((m) => m.identifier === form.getFieldValue('modelName'));
  const summaryScan = form.getFieldValue('entryScanConfig') as
    | (EntryScanConfig & Record<string, unknown>)
    | undefined;
  const summaryRequire = form.getFieldValue('requireHierarchyReview');

  const modularizeDisplay = selectedSystem
    ? resolveBoundName(selectedSystem.modularizePromptId, undefined, modularizeById)
    : { name: '请先选择系统', isDefault: true };
  const documentDisplay = selectedSystem
    ? resolveBoundName(selectedSystem.documentPromptId, undefined, documentById)
    : { name: '请先选择系统', isDefault: true };

  const systemStateWarn =
    selectedSystem && selectedSystem.state && selectedSystem.state !== 'ACTIVE'
      ? `系统当前状态为「${selectedSystem.state}」，未启用，无法创建任务。`
      : null;

  return (
    <div className="ci-page ci-task-dispatch-page">
      <Card
        title={
          <Space>
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate('/tasks/query')}
            >
              返回任务查询
            </Button>
            <span style={{ fontWeight: 600 }}>手动下发：创建反编译任务</span>
          </Space>
        }
      >
        <Steps
          current={currentStep}
          size="small"
          items={[{ title: '来源' }, { title: '策略' }, { title: '确认' }]}
          style={{ marginBottom: 24 }}
        />

        <Form
          form={form}
          layout="vertical"
          preserve
          initialValues={{
            taskType: 'INITIAL',
            entryScanConfig: { excludeClasspaths: DEFAULT_EXCLUDE_CLASSPATHS },
            requireHierarchyReview: true,
            modelName: models.find((m) => m.isDefault === 'true')?.identifier,
          }}
        >
          {currentStep === 0 && (
            <Row gutter={[16, 16]}>
              <Col xs={24} md={12}>
                <Form.Item
                  name="systemId"
                  label="系统"
                  rules={[{ required: true, message: '请选择系统' }]}
                >
                  <Select
                    placeholder="请选择已配置代码库且已启用的系统"
                    showSearch
                    optionFilterProp="label"
                    notFoundContent="暂无已配置代码库的启用系统"
                    options={taskSourceSystems.map((s) => ({
                      value: s.id,
                      label: `${s.name}${s.state && s.state !== 'ACTIVE' ? ` (${s.state})` : ''}`,
                    }))}
                  />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item
                  name="repositoryId"
                  label="代码库"
                  rules={[{ required: true, message: '请选择代码库' }]}
                >
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
              </Col>
            </Row>
          )}

          {currentStep === 1 && (
            <>
              {systemStateWarn && (
                <Alert
                  type="error"
                  showIcon
                  style={{ marginBottom: 16 }}
                  message={systemStateWarn}
                  description="请到「系统与仓库」完成配置并启用，或选择其他已启用 (ACTIVE) 的系统。"
                />
              )}

              {/* 系统绑定的提示词：只读展示 */}
              <Card size="small" style={{ marginBottom: 16, background: '#fafafa' }}>
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    系统已绑定提示词（任务创建后按 任务显式 → 系统绑定 → 默认提示词 顺序解析）
                  </Text>
                  <Space wrap>
                    <Tooltip title="AI_ANALYZING / MODULE_HIERARCHY 阶段使用">
                      <Tag color="geekblue" icon={modularizeDisplay.isDefault ? undefined : '✓'}>
                        模块提取：{modularizeDisplay.name}
                      </Tag>
                    </Tooltip>
                    <Tooltip title="GENERATING_DOC 阶段使用">
                      <Tag color="geekblue" icon={documentDisplay.isDefault ? undefined : '✓'}>
                        文档生成：{documentDisplay.name}
                      </Tag>
                    </Tooltip>
                    <Button
                      type="link"
                      size="small"
                      onClick={() => navigate(`/basic/prompts/defaults`)}
                    >
                      前往「基础配置 → 默认提示词维护」
                    </Button>
                  </Space>
                </Space>
              </Card>

              <Row gutter={[16, 16]}>
                <Col xs={24} md={12}>
                  <Form.Item
                    name="taskType"
                    label="任务类型"
                    initialValue="INITIAL"
                    rules={[{ required: true }]}
                  >
                    <Select
                      options={[
                        { value: 'INITIAL', label: '全量扫描 - 分析整个代码库' },
                        { value: 'INCREMENTAL', label: '增量扫描 - 基于 Git Diff 分析' },
                      ]}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item
                    name="modelName"
                    label="AI模型"
                    initialValue={models.find((m) => m.isDefault === 'true')?.identifier}
                    rules={[{ required: true, message: '请选择AI模型' }]}
                  >
                    <Select
                      placeholder="请选择要调用的 AI 模型"
                      options={models.map((m) => ({
                        value: m.identifier,
                        label: `${m.name} (${m.identifier})`,
                      }))}
                    />
                  </Form.Item>
                </Col>
              </Row>

              <div className="ci-scan-config">
                <div
                  className="ci-scan-config-title"
                  style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
                >
                  <div>
                    <Text type="secondary" style={{ fontSize: 12 }}>扫描规则</Text>
                    <Tooltip title="配置仅对该任务生效；任一入口列表为空即走默认 Controller/JOB/MQ 识别">
                      <Text type="secondary" style={{ fontSize: 12, marginLeft: 6 }}>（不配置走默认）</Text>
                    </Tooltip>
                  </div>
                  <Button
                    size="small"
                    icon={<SyncOutlined />}
                    onClick={handleSyncRepoScanConfig}
                    disabled={!selectedRepositoryId}
                  >
                    同步仓库配置
                  </Button>
                </div>
                <Row gutter={[8, 6]}>
                  <Col xs={24} md={12}>
                    <div className="ci-scan-config-col">
                      <div className="ci-scan-config-col-title">入口识别（满足任一即视为入口）</div>
                      <div className="ci-scan-config-row">
                        <span className="ci-scan-config-label">注解</span>
                        <Form.Item name={['entryScanConfig', 'includeAnnotations']} noStyle>
                          <Select mode="tags" placeholder="RestController / Service / ..." style={{ width: '100%' }} />
                        </Form.Item>
                      </div>
                      <div className="ci-scan-config-row">
                        <span className="ci-scan-config-label">类路径</span>
                        <Form.Item name={['entryScanConfig', 'includeClasspaths']} noStyle>
                          <Select mode="tags" placeholder="com.demo.controller.**" style={{ width: '100%' }} />
                        </Form.Item>
                      </div>
                      <div className="ci-scan-config-row">
                        <span className="ci-scan-config-label">继承/实现</span>
                        <Form.Item name={['entryScanConfig', 'includeExtends']} noStyle>
                          <Select mode="tags" placeholder="BaseEntry / CommandLineRunner" style={{ width: '100%' }} />
                        </Form.Item>
                      </div>
                    </div>
                  </Col>
                  <Col xs={24} md={12}>
                    <div className="ci-scan-config-col">
                      <div className="ci-scan-config-col-title">排除规则（满足任一即从候选中排除）</div>
                      <div className="ci-scan-config-row">
                        <span className="ci-scan-config-label">类路径</span>
                        <Form.Item name={['entryScanConfig', 'excludeClasspaths']} noStyle>
                          <Select mode="tags" placeholder="*.test.*" style={{ width: '100%' }} />
                        </Form.Item>
                      </div>
                      <div className="ci-scan-config-row">
                        <span className="ci-scan-config-label">包路径</span>
                        <Form.Item name={['entryScanConfig', 'excludePackages']} noStyle>
                          <Select mode="tags" placeholder="com.legacy.config" style={{ width: '100%' }} />
                        </Form.Item>
                      </div>
                      <div className="ci-scan-config-row">
                        <span className="ci-scan-config-label">注解</span>
                        <Form.Item name={['entryScanConfig', 'excludeAnnotations']} noStyle>
                          <Select mode="tags" placeholder="Internal / Deprecated" style={{ width: '100%' }} />
                        </Form.Item>
                      </div>
                    </div>
                  </Col>
                </Row>
              </div>

              <Form.Item
                name="requireHierarchyReview"
                label="模块层级调试"
                tooltip="AI 提炼模块层级完成后是否暂停等待人工调试。开启后任务停在「模块层级调试」状态，需在任务列表中点击「开始调试」确认后才继续生成文档。"
                initialValue={true}
                valuePropName="checked"
                style={{ marginTop: 12, marginBottom: 0 }}
              >
                <Switch checkedChildren="启用" unCheckedChildren="跳过" />
              </Form.Item>
            </>
          )}

          {currentStep === 2 && (
            <div className="ci-confirm-box">
              <p>系统：<b>{summarySystem ?? '未选择'}</b></p>
              <p>代码库：<b>{summaryRepo ?? '未选择'}</b></p>
              <p>
                策略：<b>{summaryTaskType === 'INITIAL' ? '全量扫描' : summaryTaskType === 'INCREMENTAL' ? '增量扫描' : '未选择'}</b>
              </p>
              <p>
                模块提取提示词：<b>{modularizeDisplay.name}</b>
              </p>
              <p>
                文档生成提示词：<b>{documentDisplay.name}</b>
              </p>
              <p>
                AI模型：<b>{summaryModel?.name || form.getFieldValue('modelName') || '未选择'}</b>
              </p>
              <p>
                扫描规则：
                <b>
                  {(() => {
                    const c = (summaryScan ?? {}) as Record<string, unknown>;
                    const labels: Array<[string, unknown]> = [
                      ['入口注解', c.includeAnnotations],
                      ['入口类路径', c.includeClasspaths],
                      ['入口继承/实现', c.includeExtends],
                      ['排除类路径', c.excludeClasspaths],
                      ['排除包路径', c.excludePackages],
                      ['排除注解', c.excludeAnnotations],
                    ];
                    const configured = labels.filter(
                      ([, v]) => Array.isArray(v) && (v as unknown[]).length > 0,
                    );
                    return configured.length === 0
                      ? '使用默认（Controller/JOB/MQ 兜底）'
                      : configured.map(([k, v]) => `${k} ${(v as unknown[]).length} 条`).join('，');
                  })()}
                </b>
              </p>
              <p>
                模块层级调试：
                <b>
                  {summaryRequire === false
                    ? '跳过（AI 提炼后直接生成文档）'
                    : '启用（AI 提炼后停在「模块层级调试」等待人工确认）'}
                </b>
              </p>
              <Text type="secondary">任务将以草稿状态创建，可在「任务查询」列表中手动启动。</Text>
            </div>
          )}
        </Form>

        <div
          style={{
            marginTop: 24,
            display: 'flex',
            justifyContent: 'flex-end',
            borderTop: '1px solid #f0f0f0',
            paddingTop: 16,
          }}
        >
          <Space>
            {currentStep > 0 && <Button onClick={() => setCurrentStep((s) => s - 1)}>上一步</Button>}
            {currentStep < 2 ? (
              <Button type="primary" loading={readinessLoading} onClick={handleNext}>
                下一步
              </Button>
            ) : (
              <Button type="primary" loading={submitting} onClick={handleSubmit}>
                创建任务
              </Button>
            )}
          </Space>
        </div>
      </Card>

      {/* 就绪度拦截弹窗 */}
      {readinessModalOpen && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.45)',
            zIndex: 1100,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
          onClick={() => setReadinessModalOpen(false)}
        >
          <Card
            style={{ width: 640 }}
            title={
              <Space>
                <CloseCircleOutlined style={{ color: '#c4485d' }} />
                <span>无法新建任务：尚有未确认的草稿</span>
              </Space>
            }
            extra={
              <Space>
                <Button onClick={() => setReadinessModalOpen(false)}>关闭</Button>
                <Button
                  type="primary"
                  onClick={() => {
                    setReadinessModalOpen(false);
                    navigate('/drafts');
                  }}
                >
                  前往复核工作区
                </Button>
              </Space>
            }
            onClick={(e) => e.stopPropagation()}
          >
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
          </Card>
        </div>
      )}

      <Alert
        type="info"
        showIcon
        style={{ marginTop: 16 }}
        message="小贴士：提示词由系统级绑定，未设置时回退到「基础配置 → 默认提示词」页面设定的默认项；创建后请到「任务查询」中点击「启动」按钮开始执行。"
      />
    </div>
  );
};

export default TaskDispatchPage;
