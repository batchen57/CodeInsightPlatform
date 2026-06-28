import React from 'react';
import { Button, Drawer, Popconfirm, Space } from 'antd';
import { CheckCircleOutlined } from '@ant-design/icons';
import ModuleHierarchyEditor from './ModuleHierarchyEditor';

export interface ModuleHierarchyEditorDrawerProps {
  open: boolean;
  taskId: number | null;
  onClose: () => void;
  /** 保存并继续后通知父组件刷新列表 */
  onSubmitted?: () => void;
}

/**
 * 模块层级调试抽屉（薄壳）：在 ModuleHierarchyEditor 外层套一个 Ant Design Drawer，
 * 顶部提供「保存并继续」+「取消」动作条，便于在「模块层级复核」列表页与「反编译任务」列表中复用。
 */
const ModuleHierarchyEditorDrawer: React.FC<ModuleHierarchyEditorDrawerProps> = ({
  open,
  taskId,
  onClose,
  onSubmitted,
}) => {
  return (
    <Drawer
      title={taskId ? `模块层级调试 #${taskId}` : '模块层级调试'}
      open={open}
      width={960}
      onClose={onClose}
      destroyOnHidden
      extra={
        <Space>
          <Button onClick={onClose}>取消</Button>
        </Space>
      }
    >
      {taskId != null && (
        <ModuleHierarchyEditor
          taskId={taskId}
          onSubmitted={() => {
            onClose();
            onSubmitted?.();
          }}
          renderSubmit={(handleSubmit, saving) => (
            <Popconfirm
              title="确认提交并继续生成文档？"
              description="保存当前修改后将进入 GENERATING_DOC，无法再返回调试状态。"
              okText="确认并继续"
              cancelText="再检查下"
              onConfirm={handleSubmit}
            >
              <Button type="primary" icon={<CheckCircleOutlined />} loading={saving}>
                保存并继续
              </Button>
            </Popconfirm>
          )}
        />
      )}
    </Drawer>
  );
};

export default ModuleHierarchyEditorDrawer;