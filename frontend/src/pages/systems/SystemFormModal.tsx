import React from 'react';
import { Form, Input, Modal } from 'antd';
import type { FormInstance } from 'antd';
import type { System } from '../../types';

export interface SystemFormValues {
  name: string;
  owner: string;
  description?: string;
}

interface Props {
  open: boolean;
  editing: System | null;
  form: FormInstance<SystemFormValues>;
  onCancel: () => void;
  onSubmit: () => Promise<void>;
}

/** 创建 / 编辑系统 Modal。表单实例由父组件持有，便于外部 setFieldsValue */
const SystemFormModal: React.FC<Props> = ({ open, editing, form, onCancel, onSubmit }) => (
  <Modal
    title={editing ? '编辑系统' : '创建系统'}
    open={open}
    onOk={onSubmit}
    onCancel={onCancel}
    destroyOnHidden
  >
    <Form<SystemFormValues> form={form} layout="vertical" style={{ marginTop: 16 }}>
      <Form.Item
        name="name"
        label="系统名称"
        rules={[{ required: true, message: '请输入系统名称' }]}
      >
        <Input placeholder="order-service" />
      </Form.Item>
      <Form.Item
        name="owner"
        label="负责人"
        rules={[{ required: true, message: '请输入负责人' }]}
      >
        <Input placeholder="开发负责人" />
      </Form.Item>
      <Form.Item name="description" label="描述">
        <Input.TextArea rows={3} placeholder="核心业务职责和技术范围" />
      </Form.Item>
    </Form>
  </Modal>
);

export default SystemFormModal;
