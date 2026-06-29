import type React from 'react';
import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Col,
  Form,
  Input,
  Modal,
  Radio,
  Row,
  Select,
  Switch,
  message,
} from 'antd';
import { listSystems } from '../../api/system';
import { listRepositories } from '../../api/repository';
import { listPrompts } from '../../api/prompt';
import { listModels } from '../../api/model';
import {
  createSchedule,
  updateSchedule,
  type ScheduleCreatePayload,
  type ScheduleUpdatePayload,
} from '../../api/schedule';
import type {
  AiModel,
  EntryScanConfig,
  FireStrategy,
  OverlapStrategy,
  Repository,
  ScheduleTask,
  System,
} from '../../types';
import EntryScanConfigEditor from '../../components/EntryScanConfigEditor';
import { cronPresets, isValidCron } from './cron-presets';

interface Props {
  open: boolean;
  editing: ScheduleTask | null;
  onClose: () => void;
  onSaved: () => void;
}

/**
 * 新增 / 编辑定时任务共用 Modal。
 * 列表页与详情页都通过该组件触发。
 */
const ScheduleFormModal: React.FC<Props> = ({ open, editing, onClose, onSaved }) => {
  const [form] = Form.useForm<ScheduleCreatePayload>();

  const [systems, setSystems] = useState<System[]>([]);
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [prompts, setPrompts] = useState<{ id: number; name: string; promptType?: string }[]>([]);
  const [models, setModels] = useState<AiModel[]>([]);

  useEffect(() => {
    if (!open) return;
    Promise.all([
      listSystems({ current: 1, size: 100 }),
      listRepositories({ current: 1, size: 200 }),
      listPrompts({ current: 1, size: 200 }),
      listModels(),
    ])
      .then(([sysPage, repoPage, promptList, modelList]) => {
        setSystems(sysPage.records);
        setRepositories(repoPage.records);
        setPrompts(
          promptList.records.map((p) => ({ id: p.id, name: p.name, promptType: p.promptType })),
        );
        setModels(modelList);
      })
      .catch(() => {
        // ignore
      });
  }, [open]);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue({
        systemId: editing.systemId,
        repositoryId: editing.repositoryId,
        name: editing.name,
        description: editing.description,
        cronExpression: editing.cronExpression,
        timezone: editing.timezone,
        enabled: editing.enabled === 1,
        fireStrategy: editing.fireStrategy,
        overlapStrategy: editing.overlapStrategy,
        modularizePromptId: editing.modularizePromptId,
        documentPromptId: editing.documentPromptId,
        modelName: editing.modelName,
        entryScanConfig: editing.entryScanConfig as EntryScanConfig | undefined,
        requireHierarchyReview: editing.requireHierarchyReview === 1,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        timezone: 'Asia/Shanghai',
        enabled: true,
        fireStrategy: 'INCREMENTAL' as FireStrategy,
        overlapStrategy: 'SKIP' as OverlapStrategy,
        requireHierarchyReview: true,
      });
    }
  }, [open, editing, form]);

  const watchedSystemId = Form.useWatch('systemId', form);
  const availableRepositories = useMemo(
    () => repositories.filter((r) => !watchedSystemId || r.systemId === watchedSystemId),
    [repositories, watchedSystemId],
  );

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (!isValidCron(values.cronExpression)) {
        message.error('cron 表达式不合法，请检查');
        return;
      }
      if (editing) {
        const payload: ScheduleUpdatePayload = { ...values };
        await updateSchedule(editing.id, payload);
        message.success('已更新');
      } else {
        await createSchedule({ ...values });
        message.success('已创建');
      }
      onSaved();
    } catch (err) {
      // ignore
    }
  };

  return (
    <Modal
      title={editing ? `编辑定时任务 #${editing.id}` : '新建定时任务'}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      okText="保存"
      cancelText="取消"
      width={780}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          timezone: 'Asia/Shanghai',
          enabled: true,
          fireStrategy: 'INCREMENTAL',
          overlapStrategy: 'SKIP',
          requireHierarchyReview: true,
        }}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="每次触发会自动创建一条反编译任务记录，可在『手动下发』列表通过 SCHEDULED 标签筛选查看。"
        />

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              label="配置名"
              name="name"
              rules={[{ required: true, message: '请输入配置名' }]}
            >
              <Input placeholder="如：每日凌晨全量重构" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="描述" name="description">
              <Input placeholder="可选" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={14}>
            <Form.Item
              label="cron 表达式"
              name="cronExpression"
              tooltip="Spring 6 位：秒 分 时 日 月 周（例：0 0 2 * * * = 每天凌晨 2 点）"
              rules={[
                { required: true, message: '请输入 cron 表达式' },
                {
                  validator: (_rule, value) =>
                    !value || isValidCron(value)
                      ? Promise.resolve()
                      : Promise.reject(new Error('cron 表达式不合法')),
                },
              ]}
            >
              <Input placeholder="0 0 2 * * *" />
            </Form.Item>
          </Col>
          <Col span={10}>
            <Form.Item label="常用预设" shouldUpdate noStyle>
              {({ setFieldValue }) => (
                <Select
                  placeholder="选择预设"
                  style={{ width: '100%' }}
                  onChange={(v) => setFieldValue('cronExpression', v)}
                  options={cronPresets}
                />
              )}
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={8}>
            <Form.Item label="时区" name="timezone">
              <Input placeholder="Asia/Shanghai" />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item label="触发策略" name="fireStrategy">
              <Radio.Group>
                <Radio.Button value="INCREMENTAL">增量</Radio.Button>
                <Radio.Button value="INITIAL">全量</Radio.Button>
              </Radio.Group>
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item label="启用" name="enabled" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={24}>
            <Form.Item
              label="冲突策略"
              name="overlapStrategy"
              tooltip="上一次任务尚未结束时本次触发的处置方式"
            >
              <Radio.Group>
                <Radio.Button value="SKIP">跳过</Radio.Button>
                <Radio.Button value="QUEUE">排队</Radio.Button>
                <Radio.Button value="PARALLEL">并行</Radio.Button>
              </Radio.Group>
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              label="系统"
              name="systemId"
              rules={[{ required: true, message: '请选择系统' }]}
            >
              <Select
                placeholder="选择系统"
                options={systems.map((s) => ({ label: s.name, value: s.id }))}
                onChange={() => form.setFieldValue('repositoryId', undefined)}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              label="代码库"
              name="repositoryId"
              rules={[{ required: true, message: '请选择代码库' }]}
            >
              <Select
                placeholder="选择代码库"
                options={availableRepositories.map((r) => ({
                  label: r.gitUrl,
                  value: r.id,
                }))}
              />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item label="模块提取提示词" name="modularizePromptId">
              <Select
                allowClear
                placeholder="不选则走默认"
                options={prompts
                  .filter((p) => p.promptType === 'MODULARIZE')
                  .map((p) => ({ label: p.name, value: p.id }))}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="文档生成提示词" name="documentPromptId">
              <Select
                allowClear
                placeholder="不选则走默认"
                options={prompts
                  .filter((p) => p.promptType === 'DOCUMENT_GENERATION')
                  .map((p) => ({ label: p.name, value: p.id }))}
              />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item label="AI 模型" name="modelName">
              <Select
                allowClear
                placeholder="不选则走系统默认模型"
                options={models.map((m) => ({
                  label: `${m.name} (${m.identifier})`,
                  value: m.identifier,
                }))}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              label="模块层级调试断点"
              name="requireHierarchyReview"
              valuePropName="checked"
            >
              <Switch checkedChildren="启用" unCheckedChildren="跳过" />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item label="入口扫描配置（可选）" name="entryScanConfig">
          <EntryScanConfigEditor />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ScheduleFormModal;