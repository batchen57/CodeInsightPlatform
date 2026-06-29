import React from 'react';
import {
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Switch,
} from 'antd';
import type { FormInstance } from 'antd';
import type { AiModelPreset } from '../../../types';

export interface ModelPresetFormValues {
  name: string;
  provider: string;
  identifier: string;
  baseUrl?: string;
  capabilities: string[];
  description?: string;
  sortOrder?: number;
  status: boolean;
}

interface Props {
  open: boolean;
  editing?: AiModelPreset | null;
  form: FormInstance<ModelPresetFormValues>;
  onCancel: () => void;
  onSubmit: () => Promise<void>;
}

const capabilityOptions = [
  { label: '文本识别', value: 'text' },
  { label: '图片识别', value: 'image' },
  { label: '视频识别', value: 'video' },
];

const ModelPresetModal: React.FC<Props> = ({
  open,
  editing,
  form,
  onCancel,
  onSubmit,
}) => (
  <Modal
    title={editing ? '编辑模型预设' : '新增模型预设'}
    open={open}
    onOk={onSubmit}
    onCancel={onCancel}
    width={640}
    destroyOnHidden
  >
    <Form<ModelPresetFormValues> form={form} layout="vertical" style={{ marginTop: 16 }}>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            name="name"
            label="预设名称"
            rules={[{ required: true, message: '请输入预设名称' }]}
          >
            <Input placeholder="如: Claude Sonnet 4.5" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            name="provider"
            label="技术供应商"
            rules={[{ required: true, message: '请输入技术供应商' }]}
          >
            <Input placeholder="如: Anthropic" />
          </Form.Item>
        </Col>
      </Row>

      <Form.Item
        name="identifier"
        label="模型调用 ID"
        rules={[{ required: true, message: '请输入模型调用 ID' }]}
      >
        <Input placeholder="如: claude-sonnet-4-5" />
      </Form.Item>

      <Form.Item name="baseUrl" label="Endpoint URL">
        <Input placeholder="https://api.example.com/v1" />
      </Form.Item>

      <Form.Item
        name="capabilities"
        label="支持能力"
        rules={[{ required: true, message: '请选择支持能力' }]}
      >
        <Select mode="multiple" placeholder="选择支持的能力" options={capabilityOptions} />
      </Form.Item>

      <Form.Item name="description" label="模板说明">
        <Input.TextArea rows={3} placeholder="描述该预设适合的代码洞察场景..." />
      </Form.Item>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item name="sortOrder" label="排序权重">
            <InputNumber min={0} precision={0} style={{ width: '100%' }} placeholder="数字越小越靠前" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="status" label="是否启用" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
        </Col>
      </Row>
    </Form>
  </Modal>
);

export default ModelPresetModal;
