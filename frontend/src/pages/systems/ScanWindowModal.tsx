import React, { useEffect, useState } from 'react';
import { Button, Form, Modal, Select, Switch, message } from 'antd';
import { getScanWindow, upsertScanWindow } from '../../api/scan-window';

const WEEK_OPTIONS = [
  { value: 1, label: '周一' }, { value: 2, label: '周二' },
  { value: 4, label: '周三' }, { value: 8, label: '周四' },
  { value: 16, label: '周五' }, { value: 32, label: '周六' },
  { value: 64, label: '周日' },
];

interface Props {
  open: boolean;
  repositoryId: number;
  onClose: () => void;
  onSaved?: () => void;
}

const ScanWindowModal: React.FC<Props> = ({ open, repositoryId, onClose, onSaved }) => {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open || !repositoryId) return;
    getScanWindow(repositoryId)
      .then((w) => {
        if (w) {
          form.setFieldsValue({
            weekDays: weekBitsToList(w.weekDays),
            time: `${String(w.hour).padStart(2, '0')}:${String(w.minute).padStart(2, '0')}`,
            enabled: w.enabled ?? true,
          });
        } else {
          form.resetFields();
        }
      })
      .catch(() => form.resetFields());
  }, [open, repositoryId, form]);

  const handleSave = async () => {
    const values = await form.validateFields();
    const weekDays = (values.weekDays as number[]).reduce((s, v) => s + v, 0);
    const [h, m] = (values.time as string).split(':').map(Number);
    setSaving(true);
    try {
      await upsertScanWindow({ repositoryId, weekDays, hour: h, minute: m, enabled: values.enabled });
      message.success('时间窗口已保存');
      onSaved?.();
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="仓库扫描时间窗口" open={open} onCancel={onClose} width={480} destroyOnClose
      footer={[
        <Button key="cancel" onClick={onClose}>取消</Button>,
        <Button key="save" type="primary" loading={saving} onClick={handleSave}>保存</Button>,
      ]}
    >
      <Form form={form} layout="vertical" initialValues={{ enabled: true }}>
        <Form.Item name="weekDays" label="触发周几" rules={[{ required: true, message: '请选择周几' }]}>
          <Select mode="multiple" placeholder="选择周几" options={WEEK_OPTIONS} />
        </Form.Item>
        <Form.Item name="time" label="触发时间" rules={[{ required: true, message: '请选择时间' }]}>
          <Select
            placeholder="00:00"
            options={Array.from({ length: 48 }, (_, i) => {
              const h = Math.floor(i / 2);
              const m = i % 2 === 0 ? 0 : 30;
              const v = `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
              return { value: v, label: v };
            })}
          />
        </Form.Item>
        <Form.Item name="enabled" label="启用" valuePropName="checked">
          <Switch />
        </Form.Item>
      </Form>
    </Modal>
  );
};

function weekBitsToList(bits: number): number[] {
  return WEEK_OPTIONS.filter((o) => (bits & o.value) !== 0).map((o) => o.value);
}

export default ScanWindowModal;
