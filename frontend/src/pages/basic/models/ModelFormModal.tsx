import React from 'react';
import {
  Button,
  Col,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Switch,
  Tooltip,
} from 'antd';
import type { FormInstance } from 'antd';
import { PlusOutlined, SettingOutlined } from '@ant-design/icons';
import type { AiModel, AiModelPreset } from '../../../types';

export interface ModelFormValues {
  name: string;
  provider: string;
  identifier: string;
  baseUrl?: string;
  apiKey?: string;
  capabilities: string[];
  description?: string;
  isDefault: boolean;
}

interface Props {
  open: boolean;
  editing: AiModel | null;
  form: FormInstance<ModelFormValues>;
  presets: AiModelPreset[];
  presetsLoading: boolean;
  onCancel: () => void;
  onSubmit: () => Promise<void>;
  onCreatePreset: () => void;
  onManagePresets: () => void;
}

const ModelFormModal: React.FC<Props> = ({
  open,
  editing,
  form,
  presets,
  presetsLoading,
  onCancel,
  onSubmit,
  onCreatePreset,
  onManagePresets,
}) => {
  /** 一键填充预设模板 */
  const handleApplyPreset = (presetId: number) => {
    const p = presets.find((x) => x.id === presetId);
    if (p) {
      form.setFieldsValue({
        name: p.name,
        provider: p.provider,
        identifier: p.identifier,
        baseUrl: p.baseUrl,
        description: p.description,
        capabilities: p.capabilities ? p.capabilities.split(',') : [],
      });
    }
  };

  return (
    <Modal
      title={
        <Space>
          <SettingOutlined />
          <span>{editing ? '编辑模型配置' : '新增 AI 模型'}</span>
        </Space>
      }
      open={open}
      onOk={onSubmit}
      onCancel={onCancel}
      width={700}
      destroyOnHidden
    >
      <Form<ModelFormValues> form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item label="从官方预设模板快速加载">
              <Space.Compact style={{ width: '100%' }}>
                <Select
                  placeholder="选择一个预设模板填充..."
                  onChange={handleApplyPreset}
                  allowClear
                  loading={presetsLoading}
                  style={{ flex: 1 }}
                  options={presets.map((p) => ({
                    label: `${p.name} · ${p.provider}`,
                    value: p.id,
                  }))}
                />
                <Tooltip title="新增预设">
                  <Button icon={<PlusOutlined />} onClick={onCreatePreset} />
                </Tooltip>
                <Tooltip title="管理预设">
                  <Button icon={<SettingOutlined />} onClick={onManagePresets} />
                </Tooltip>
              </Space.Compact>
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="name"
              label="模型显示名称"
              rules={[{ required: true, message: '请输入模型显示名称' }]}
            >
              <Input placeholder="如: Gemini 2.0 Pro" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="provider"
              label="技术供应商"
              rules={[{ required: true, message: '请输入技术供应商' }]}
            >
              <Input placeholder="如: Google" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="identifier"
              label="模型调用 ID"
              rules={[{ required: true, message: '请输入模型调用 ID' }]}
            >
              <Input placeholder="如: gemini-2.0-pro-exp-02-05" />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="baseUrl" label="Endpoint URL (接口地址)">
          <Input placeholder="https://generativelanguage.googleapis.com" />
        </Form.Item>

        <Form.Item
          name="apiKey"
          label="API Key (密钥)"
          extra="出于安全考虑，后端不会回显已保存密钥；编辑时留空则不修改。"
        >
          <Input.Password
            autoComplete="new-password"
            placeholder={editing?.hasApiKey ? '已配置密钥，留空则不修改' : '在此输入您的密钥'}
          />
        </Form.Item>

        <Form.Item
          name="capabilities"
          label="支持能力"
          rules={[{ required: true, message: '请选择支持能力' }]}
        >
          <Select
            mode="multiple"
            placeholder="选择支持的能力"
            options={[
              { label: '文本识别', value: 'text' },
              { label: '图片识别', value: 'image' },
              { label: '视频识别', value: 'video' },
            ]}
          />
        </Form.Item>

        <Form.Item name="description" label="功能描述">
          <Input.TextArea rows={3} placeholder="描述该模型的分析优势、建议场景等..." />
        </Form.Item>

        <Form.Item name="isDefault" label="是否默认模型" valuePropName="checked">
          <Switch checkedChildren="是" unCheckedChildren="否" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ModelFormModal;
