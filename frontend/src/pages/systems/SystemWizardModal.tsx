import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Steps,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  CheckCircleOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { createSystem, updateSystem } from '../../api/system';
import {
  createRepository,
  updateRepository,
  testRepositoryConnection,
} from '../../api/repository';
import { listPrompts } from '../../api/prompt';
import type { Repository, System, EntryScanConfig, Prompt } from '../../types';
import EntryScanConfigEditor from '../../components/EntryScanConfigEditor';

const { Text, Paragraph } = Typography;

const DEFAULT_EXCLUDE = ['**/*Test', '**/*Tests', '**/*TestCase'];

const DEFAULT_PROMPTS: Record<'MODULARIZE' | 'DOCUMENT_GENERATION', { key: string; label: string }> = {
  MODULARIZE: { key: 'MODULARIZE', label: '模块提取提示词' },
  DOCUMENT_GENERATION: { key: 'DOCUMENT_GENERATION', label: '文档生成提示词' },
};

interface Props {
  open: boolean;
  /** 已有系统 ID（空 = 新建） */
  initialSystemId?: number | null;
  onClose: () => void;
  onCompleted: (systemId: number) => void;
}

interface SystemFormValues {
  name: string;
  nameCn?: string;
  owner: string;
  description?: string;
}

interface RepositoryFormValues {
  gitUrl: string;
  branch: string;
  scanRoot: string;
  username?: string;
  password?: string;
  excludeDirs?: string;
  excludeFileTypes?: string;
}

/**
 * 「新建系统」4 步向导：
 *  Step 1 基本信息  →  POST /systems（state=DRAFT）
 *  Step 2 配置仓库  →  POST /repositories（state=REPO_CONFIGURED）
 *  Step 3 入口扫描  →  PUT /repositories/{id} {entryScanConfig}（state=SCAN_CONFIGURED）
 *  Step 4 提示词    →  PUT /systems/{id} {modularizePromptId, documentPromptId}（state=PROMPT_CONFIGURED）
 *
 * 状态机推进由后端自动完成；前端每步只调一个 API。
 */
const SystemWizardModal: React.FC<Props> = ({ open, initialSystemId, onClose, onCompleted }) => {
  const [currentStep, setCurrentStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);

  const [systemId, setSystemId] = useState<number | null>(initialSystemId ?? null);
  const [repoId, setRepoId] = useState<number | null>(null);

  const [systemForm] = Form.useForm<SystemFormValues>();
  const [repoForm] = Form.useForm<RepositoryFormValues>();
  const [scanForm] = Form.useForm<{ entryScanConfig: EntryScanConfig }>();
  const [promptForm] = Form.useForm<{
    modularizePromptId?: number | null;
    documentPromptId?: number | null;
  }>();

  const [prompts, setPrompts] = useState<Prompt[]>([]);
  const [promptsLoading, setPromptsLoading] = useState(false);

  // 重置 wizard 状态
  useEffect(() => {
    if (!open) return;
    setCurrentStep(0);
    setSystemId(initialSystemId ?? null);
    setRepoId(null);
    systemForm.resetFields();
    repoForm.resetFields();
    scanForm.resetFields();
    promptForm.resetFields();
  }, [open, initialSystemId, systemForm, repoForm, scanForm, promptForm]);

  // Step 4：拉取可用提示词
  const fetchPrompts = useCallback(async () => {
    setPromptsLoading(true);
    try {
      const all: Prompt[] = [];
      for (const t of Object.values(DEFAULT_PROMPTS)) {
        const res = await listPrompts({ current: 1, size: 200, status: 1, promptType: t.key });
        all.push(...res.records);
      }
      setPrompts(all);
    } catch {
      // 拦截器已提示
    } finally {
      setPromptsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open && currentStep === 3) fetchPrompts();
  }, [open, currentStep, fetchPrompts]);

  const defaultModularize = useMemo(
    () => prompts.find((p) => p.promptType === 'MODULARIZE' && p.isDefault === 1) || null,
    [prompts],
  );
  const defaultDocument = useMemo(
    () => prompts.find((p) => p.promptType === 'DOCUMENT_GENERATION' && p.isDefault === 1) || null,
    [prompts],
  );

  const modularizeOptions = useMemo(
    () => prompts
      .filter((p) => p.promptType === 'MODULARIZE' && p.status === 1)
      .map((p) => ({
        value: p.id,
        label: `${p.name} (v${p.version})${p.isDefault === 1 ? ' · 默认' : ''}`,
      })),
    [prompts],
  );
  const documentOptions = useMemo(
    () => prompts
      .filter((p) => p.promptType === 'DOCUMENT_GENERATION' && p.status === 1)
      .map((p) => ({
        value: p.id,
        label: `${p.name} (v${p.version})${p.isDefault === 1 ? ' · 默认' : ''}`,
      })),
    [prompts],
  );

  /** Step 1: 基本信息 */
  const handleStep1Submit = async () => {
    const values = await systemForm.validateFields();
    setSubmitting(true);
    try {
      let sys: System;
      if (systemId) {
        sys = await updateSystem(systemId, values);
      } else {
        sys = await createSystem(values);
        setSystemId(sys.id);
      }
      setSystemId(sys.id);
      message.success('基本信息已保存');
      setCurrentStep(1);
    } finally {
      setSubmitting(false);
    }
  };

  /** Step 2: 配置仓库 */
  const handleStep2Submit = async () => {
    const values = await repoForm.validateFields();
    setSubmitting(true);
    try {
      const repo: Repository = await createRepository({
        systemId: systemId!,
        ...values,
      } as Partial<Repository>);
      setRepoId(repo.id);
      message.success('仓库已添加');
      setCurrentStep(2);
    } finally {
      setSubmitting(false);
    }
  };

  const handleTestConnection = async () => {
    const values = await repoForm.validateFields(['gitUrl', 'branch', 'username', 'password']);
    try {
      const ok = await testRepositoryConnection(values);
      if (ok) message.success('Git 连接测试成功');
      else message.error('Git 连接测试失败');
    } catch {
      // 拦截器已处理
    }
  };

  /** Step 3: 入口扫描 */
  const handleFillDefaultScan = () => {
    scanForm.setFieldsValue({
      entryScanConfig: {
        includeAnnotations: ['RestController', 'Controller', 'RequestMapping'],
        includeClasspaths: [],
        includeExtends: [],
        excludeClasspaths: DEFAULT_EXCLUDE,
        excludePackages: [],
        excludeAnnotations: ['Deprecated'],
      },
    });
  };

  const handleStep3Submit = async () => {
    const values = await scanForm.validateFields();
    setSubmitting(true);
    try {
      await updateRepository(repoId!, {
        id: repoId!,
        entryScanConfig: values.entryScanConfig ? JSON.stringify(values.entryScanConfig) : null,
      } as Partial<Repository>);
      message.success('入口扫描规则已保存');
      setCurrentStep(3);
    } finally {
      setSubmitting(false);
    }
  };

  const handleStep4Submit = async () => {
    const values = await promptForm.validateFields();
    setSubmitting(true);
    try {
      await updateSystem(systemId!, {
        modularizePromptId: values.modularizePromptId ?? null,
        documentPromptId: values.documentPromptId ?? null,
      });
      message.success('提示词已绑定，系统进入「已配提示词」状态');
      onCompleted(systemId!);
    } finally {
      setSubmitting(false);
    }
  };

  const handleNext = () => {
    if (currentStep === 0) handleStep1Submit();
    else if (currentStep === 1) handleStep2Submit();
    else if (currentStep === 2) handleStep3Submit();
  };

  const handleCancel = () => {
    if (currentStep > 0 && systemId) {
      Modal.confirm({
        title: '确认关闭？',
        content: '已配置的步骤会保留，下次打开可继续。',
        okText: '关闭',
        cancelText: '继续配置',
        onOk: onClose,
      });
    } else {
      onClose();
    }
  };

  return (
    <Modal
      title="新建系统"
      open={open}
      onCancel={handleCancel}
      footer={null}
      width={780}
      destroyOnHidden
    >
      <Steps
        current={currentStep}
        size="small"
        style={{ marginBottom: 24 }}
        items={[
          { title: '基本信息' },
          { title: '配置仓库' },
          { title: '入口扫描' },
          { title: '提示词' },
        ]}
      />

      {/* Step 1 */}
      {currentStep === 0 && (
        <>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="填写系统基本信息。提交后系统状态将进入「草稿 (DRAFT)」。"
          />
          <Form<SystemFormValues>
            form={systemForm}
            layout="vertical"
            initialValues={{ name: '', owner: '' }}
          >
            <Form.Item name="name" label="系统名称" rules={[{ required: true, message: '请输入系统标识（如 order-service）' }]}>
              <Input placeholder="order-service" />
            </Form.Item>
            <Form.Item name="nameCn" label="中文名称">
              <Input placeholder="订单服务系统" />
            </Form.Item>
            <Form.Item name="owner" label="负责人" rules={[{ required: true, message: '请输入负责人' }]}>
              <Input placeholder="开发负责人" />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={3} placeholder="核心业务职责和技术范围" />
            </Form.Item>
          </Form>
        </>
      )}

      {/* Step 2 */}
      {currentStep === 1 && (
        <>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="添加第一个 Git 仓库。提交后系统状态将进入「已配仓库 (REPO_CONFIGURED)」。"
          />
          <Form<RepositoryFormValues>
            form={repoForm}
            layout="vertical"
            initialValues={{ branch: 'main', scanRoot: '/' }}
          >
            <Form.Item name="gitUrl" label="Git 地址" rules={[{ required: true, message: '请输入 Git 地址' }]}>
              <Input placeholder="https://github.com/xxx/yyy.git" />
            </Form.Item>
            <Form.Item name="branch" label="分支" rules={[{ required: true, message: '请输入分支' }]}>
              <Input placeholder="main" />
            </Form.Item>
            <Form.Item name="scanRoot" label="扫描根目录" rules={[{ required: true, message: '请输入扫描根目录' }]}>
              <Input placeholder="/" />
            </Form.Item>
            <Form.Item name="username" label="用户名">
              <Input placeholder="可选" />
            </Form.Item>
            <Form.Item name="password" label="密码 / 访问令牌">
              <Input.Password placeholder="可选" />
            </Form.Item>
            <Form.Item name="excludeDirs" label="排除目录">
              <Input placeholder=".git,target,test,bin" />
            </Form.Item>
            <Form.Item name="excludeFileTypes" label="排除文件类型">
              <Input placeholder=".md,.txt,.xml,.json" />
            </Form.Item>
            <Space>
              <Button icon={<SyncOutlined />} onClick={handleTestConnection}>
                测试 Git 连接
              </Button>
            </Space>
          </Form>
        </>
      )}

      {/* Step 3 */}
      {currentStep === 2 && (
        <>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="配置入口扫描规则。提交后系统状态将进入「已配扫描 (SCAN_CONFIGURED)」。"
          />
          <Form<{ entryScanConfig: EntryScanConfig }>
            form={scanForm}
            layout="vertical"
            initialValues={{
              entryScanConfig: { excludeClasspaths: DEFAULT_EXCLUDE },
            }}
          >
            <Space style={{ marginBottom: 12 }}>
              <Button icon={<SyncOutlined />} onClick={handleFillDefaultScan}>
                填入默认扫描规则
              </Button>
            </Space>
            <Form.Item
              name="entryScanConfig"
              label="入口扫描配置"
              tooltip="不配置入口识别时，运行期会走 Controller/JOB/MQ 兜底"
            >
              <EntryScanConfigEditor />
            </Form.Item>
          </Form>
        </>
      )}

      {/* Step 4 */}
      {currentStep === 3 && (
        <>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message={
              <Space direction="vertical">
                <Text>
                  为系统绑定模块提取 / 文档生成提示词。未选择时任务运行会回退到「基础配置 → 默认提示词」页面设定的默认项。
                </Text>
                {(defaultModularize || defaultDocument) && (
                  <Text type="secondary">
                    当前默认提示词：
                    {defaultModularize && <Tag color="gold">模块={defaultModularize.name} (v{defaultModularize.version})</Tag>}
                    {defaultDocument && <Tag color="gold">文档={defaultDocument.name} (v{defaultDocument.version})</Tag>}
                  </Text>
                )}
              </Space>
            }
          />
          <Form
            form={promptForm}
            layout="vertical"
            initialValues={{
              modularizePromptId: defaultModularize?.id,
              documentPromptId: defaultDocument?.id,
            }}
          >
            <Form.Item
              name="modularizePromptId"
              label={DEFAULT_PROMPTS.MODULARIZE.label}
              tooltip="AI_ANALYZING / MODULE_HIERARCHY 阶段使用；留空则使用「默认提示词」"
            >
              <Select
                allowClear
                placeholder="选择提示词（留空使用默认）"
                options={modularizeOptions}
                loading={promptsLoading}
                showSearch
                optionFilterProp="label"
              />
            </Form.Item>
            <Form.Item
              name="documentPromptId"
              label={DEFAULT_PROMPTS.DOCUMENT_GENERATION.label}
              tooltip="GENERATING_DOC 阶段使用；留空则使用「默认提示词」"
            >
              <Select
                allowClear
                placeholder="选择提示词（留空使用默认）"
                options={documentOptions}
                loading={promptsLoading}
                showSearch
                optionFilterProp="label"
              />
            </Form.Item>
          </Form>
          <Paragraph type="secondary" style={{ marginTop: 16 }}>
            提交后系统状态将进入「已配提示词 (PROMPT_CONFIGURED)」。你可以在系统列表点「启用」按钮将其激活为 ACTIVE。
          </Paragraph>
        </>
      )}

      <div
        style={{
          marginTop: 24,
          display: 'flex',
          justifyContent: 'space-between',
          borderTop: '1px solid #f0f0f0',
          paddingTop: 16,
        }}
      >
        <Button
          icon={<ArrowLeftOutlined />}
          disabled={currentStep === 0}
          onClick={() => setCurrentStep((s) => s - 1)}
        >
          上一步
        </Button>
        {currentStep < 3 ? (
          <Button type="primary" loading={submitting} onClick={handleNext} icon={<ArrowRightOutlined />}>
            下一步
          </Button>
        ) : (
          <Button
            type="primary"
            loading={submitting}
            onClick={handleStep4Submit}
            icon={<CheckCircleOutlined />}
          >
            完成配置
          </Button>
        )}
      </div>
    </Modal>
  );
};

export default SystemWizardModal;
