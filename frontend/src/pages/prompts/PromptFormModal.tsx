import { Form, Input, Modal, Switch } from 'antd';
import type { FormInstance } from 'antd/es/form';
import type React from 'react';
import type { Prompt } from '../../types';

interface PromptFormModalProps {
  open: boolean;
  promptTypeLabel: string;
  editingPrompt: Prompt | null;
  form: FormInstance;
  onSubmit: () => void;
  onCancel: () => void;
}

const PromptFormModal: React.FC<PromptFormModalProps> = ({ open, promptTypeLabel, editingPrompt, form, onSubmit, onCancel }) => (
  <Modal
    title={editingPrompt ? `编辑${promptTypeLabel}：将生成 v${editingPrompt.version + 1}` : `创建${promptTypeLabel}`}
    open={open}
    onOk={onSubmit}
    onCancel={onCancel}
    width={760}
    destroyOnClose
  >
    <Form form={form} layout="vertical" className="ci-prompt-form">
      <Form.Item name="name" label="提示词名称" rules={[{ required: true, message: '请输入提示词名称' }]}>
        <Input placeholder="Java 方法知识归纳" />
      </Form.Item>
      <Form.Item name="content" label="提示词内容" rules={[{ required: true, message: '请输入提示词内容' }]}>
        <Input.TextArea autoSize={{ minRows: 14, maxRows: 24 }} className="ci-code-input" />
      </Form.Item>
      <Form.Item name="isDefault" valuePropName="checked">
        <Switch checkedChildren="默认" unCheckedChildren="普通" />
      </Form.Item>
    </Form>
  </Modal>
);

export default PromptFormModal;
