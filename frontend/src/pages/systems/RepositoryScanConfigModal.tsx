import React, { useEffect } from 'react';
import { Button, Form, Modal, Space } from 'antd';
import type { FormInstance } from 'antd';
import { SyncOutlined } from '@ant-design/icons';
import EntryScanConfigEditor from '../../components/EntryScanConfigEditor';
import type { EntryScanConfig } from '../../types';

const DEFAULT_EXCLUDE = ['**/*Test', '**/*Tests', '**/*TestCase'];

export interface ScanConfigFormValues {
  entryScanConfig: EntryScanConfig;
}

interface Props {
  open: boolean;
  form: FormInstance<ScanConfigFormValues>;
  submitting?: boolean;
  onCancel: () => void;
  onSubmit: () => void;
}

/** 编辑仓库入口扫描规则 Modal */
const RepositoryScanConfigModal: React.FC<Props> = ({
  open,
  form,
  submitting = false,
  onCancel,
  onSubmit,
}) => {
  const DEFAULT_SCAN_CONFIG = {
    includeAnnotations: ['RestController', 'Controller', 'RequestMapping'],
    includeClasspaths: [],
    includeExtends: [],
    excludeClasspaths: DEFAULT_EXCLUDE,
    excludePackages: [],
    excludeAnnotations: ['Deprecated'],
  };

  // 打开时自动填入默认扫描规则
  useEffect(() => {
    if (open) {
      form.setFieldsValue({ entryScanConfig: DEFAULT_SCAN_CONFIG });
    }
  }, [open, form]);

  const handleFillDefault = () => {
    form.setFieldsValue({ entryScanConfig: DEFAULT_SCAN_CONFIG });
  };

  return (
    <Modal
      title="入口扫描规则"
      open={open}
      onCancel={onCancel}
      width={720}
      footer={[
        <Button key="cancel" onClick={onCancel}>
          取消
        </Button>,
        <Button key="submit" type="primary" loading={submitting} onClick={onSubmit}>
          保存
        </Button>,
      ]}
      destroyOnHidden
    >
      <Form<ScanConfigFormValues> form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Space style={{ marginBottom: 12 }}>
          <Button icon={<SyncOutlined />} onClick={handleFillDefault}>
            重置
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
    </Modal>
  );
};

export default RepositoryScanConfigModal;
