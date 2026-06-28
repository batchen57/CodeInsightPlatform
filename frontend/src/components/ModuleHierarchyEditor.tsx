import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Divider,
  Empty,
  Input,
  Popconfirm,
  Select,
  Space,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  DeleteOutlined,
  LoadingOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import {
  getModuleHierarchy,
  replaceModuleHierarchy,
  resumeModuleHierarchyReview,
} from '../api/task';
import type { FunctionNode, ModuleHierarchy, ModuleNode, SubModuleNode } from '../types';

const { Text } = Typography;

export interface ModuleHierarchyEditorProps {
  taskId: number;
  /** 提交保存并继续生成文档后通知父组件刷新数据 */
  onSubmitted?: () => void;
  /** 渲染自定义的「保存并继续」按钮（可选；不传则渲染默认 Popconfirm 按钮） */
  renderSubmit?: (handleSubmit: () => void, saving: boolean) => React.ReactNode;
  /** 渲染顶部的额外说明（可选；不传则渲染默认 Alert） */
  renderAlert?: () => React.ReactNode;
}

/**
 * 模块层级调试编辑器（无 Drawer 包装，可被任务详情页 / 复核页直接渲染）
 *
 * - 拉取 getModuleHierarchy → 编辑树 → 提交时调用 replaceModuleHierarchy + resumeModuleHierarchyReview
 * - 功能节点的 classPaths 与模块 / 子模块 / 功能名一样，**整体落表 ci_module_hierarchy**：
 *   服务重启后会由 loadByTaskId 从 DB 重建 DTO，不会丢失
 * - 父组件可自定义「保存并继续」按钮（renderSubmit），便于把动作条放到页头/页脚等位置
 */
const ModuleHierarchyEditor: React.FC<ModuleHierarchyEditorProps> = ({
  taskId,
  onSubmitted,
  renderSubmit,
  renderAlert,
}) => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [hierarchy, setHierarchy] = useState<ModuleHierarchy | null>(null);

  // 加载当前任务的模块层级
  useEffect(() => {
    if (taskId == null) return;
    let cancelled = false;
    setLoading(true);
    setHierarchy(null);
    getModuleHierarchy(taskId)
      .then((data) => {
        if (cancelled) return;
        setHierarchy(data ?? { taskId, modules: {} });
      })
      .catch(() => {
        // request.ts 拦截器已统一弹错
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [taskId]);

  const setModule = (moduleId: string, next: ModuleNode | null) => {
    setHierarchy((prev) => {
      if (!prev) return prev;
      const modules = { ...(prev.modules ?? {}) };
      if (next === null) {
        delete modules[moduleId];
      } else {
        modules[moduleId] = next;
      }
      return { ...prev, modules };
    });
  };

  const setSubModule = (moduleId: string, subId: string, next: SubModuleNode | null) => {
    setHierarchy((prev) => {
      if (!prev) return prev;
      const m = prev.modules?.[moduleId];
      if (!m) return prev;
      const subs = { ...(m.subModules ?? {}) };
      if (next === null) {
        delete subs[subId];
      } else {
        subs[subId] = next;
      }
      return { ...prev, modules: { ...prev.modules, [moduleId]: { ...m, subModules: subs } } };
    });
  };

  const setFunction = (moduleId: string, subId: string, fnId: string, next: FunctionNode | null) => {
    setHierarchy((prev) => {
      if (!prev) return prev;
      const m = prev.modules?.[moduleId];
      const sm = m?.subModules?.[subId];
      if (!m || !sm) return prev;
      const fns = { ...(sm.functions ?? {}) };
      if (next === null) {
        delete fns[fnId];
      } else {
        fns[fnId] = next;
      }
      const newSm = { ...sm, functions: fns };
      return {
        ...prev,
        modules: { ...prev.modules, [moduleId]: { ...m, subModules: { ...m.subModules, [subId]: newSm } } },
      };
    });
  };

  // 新增节点 ID：4 位 Base36 + m/s/f 前缀；后端做最终校验
  const generateCandidateId = (prefix: 'm' | 's' | 'f', existing: string[]): string => {
    const used = new Set(existing.filter((id) => id?.startsWith(prefix) && id.length === 6).map((id) => id.substring(1)));
    let body = '';
    for (let counter = 0; counter < 36 ** 4; counter++) {
      const v = counter.toString(36).padStart(4, '0');
      if (!used.has(v)) {
        body = v;
        break;
      }
    }
    if (!body) body = '0000';
    return `${prefix}${body}`;
  };

  const addModule = () => {
    setHierarchy((prev) => {
      if (!prev) return prev;
      const existingIds = Object.keys(prev.modules ?? {});
      const newId = generateCandidateId('m', existingIds);
      const newModule: ModuleNode = { id: newId, moduleName: '新模块', keywords: [], subModules: {} };
      return { ...prev, modules: { ...(prev.modules ?? {}), [newId]: newModule } };
    });
  };

  const addSubModule = (moduleId: string) => {
    setHierarchy((prev) => {
      if (!prev) return prev;
      const m = prev.modules?.[moduleId];
      if (!m) return prev;
      const existingIds = Object.keys(m.subModules ?? {});
      const newId = generateCandidateId('s', existingIds);
      const newSm: SubModuleNode = { id: newId, subModuleName: '新子模块', keywords: [], functions: {} };
      return {
        ...prev,
        modules: {
          ...prev.modules,
          [moduleId]: { ...m, subModules: { ...(m.subModules ?? {}), [newId]: newSm } },
        },
      };
    });
  };

  const addFunction = (moduleId: string, subId: string) => {
    setHierarchy((prev) => {
      if (!prev) return prev;
      const m = prev.modules?.[moduleId];
      const sm = m?.subModules?.[subId];
      if (!m || !sm) return prev;
      const existingIds = Object.keys(sm.functions ?? {});
      const newId = generateCandidateId('f', existingIds);
      const newFn: FunctionNode = { id: newId, functionName: '新功能', classPaths: [] };
      return {
        ...prev,
        modules: {
          ...prev.modules,
          [moduleId]: {
            ...m,
            subModules: {
              ...m.subModules,
              [subId]: { ...sm, functions: { ...(sm.functions ?? {}), [newId]: newFn } },
            },
          },
        },
      };
    });
  };

  // 默认保存并继续提交逻辑（父组件未传 renderSubmit 时使用）
  const handleSubmit = async () => {
    if (!hierarchy) return;
    setSaving(true);
    try {
      await replaceModuleHierarchy(taskId, hierarchy);
      await resumeModuleHierarchyReview(taskId);
      message.success('已保存并提交继续生成文档');
      onSubmitted?.();
    } finally {
      setSaving(false);
    }
  };

  const modules = useMemo(() => Object.values(hierarchy?.modules ?? {}), [hierarchy]);

  if (loading) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <LoadingOutlined /> 正在加载模块层级...
      </div>
    );
  }

  if (!hierarchy) {
    return <Empty description="未加载到模块层级" />;
  }

  return (
    <div>
      {renderAlert ? (
        renderAlert()
      ) : (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="模块层级调试说明"
          description={
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              <li>支持新增 / 删除模块、子模块、功能节点，并修改名称、关键词。</li>
              <li>功能节点的「类路径」与其它字段一样整体落表 <Text code>ci_module_hierarchy</Text>（FUNCTION 行 class_paths 列），服务重启不会丢失。</li>
              <li>「类路径」仅在调用 AI 时被剥离，不会出现在 analyze / module_doc 提示词中；只用于本服务的源码聚合（collectModuleSourceCode）。</li>
              <li>点击「保存并继续」将落表 ModuleHierarchy 并推进流水线至 GENERATING_DOC。</li>
            </ul>
          }
        />
      )}

      {renderSubmit && (
        <div style={{ marginBottom: 12, textAlign: 'right' }}>
          {renderSubmit(handleSubmit, saving)}
        </div>
      )}

      <div style={{ marginBottom: 12 }}>
        <Button type="dashed" icon={<PlusOutlined />} onClick={addModule}>
          新增模块
        </Button>
      </div>

      {modules.length === 0 && <Empty description="尚无模块，点击上方按钮新增" />}

      {modules.map((mod) => (
        <ModuleCard
          key={mod.id}
          module={mod}
          onChangeModule={(next) => setModule(mod.id, next)}
          onDeleteModule={() => setModule(mod.id, null)}
          onChangeSub={(subId, next) => setSubModule(mod.id, subId, next)}
          onAddSub={() => addSubModule(mod.id)}
          onChangeFn={(subId, fnId, next) => setFunction(mod.id, subId, fnId, next)}
          onAddFn={(subId) => addFunction(mod.id, subId)}
        />
      ))}
    </div>
  );
};

interface ModuleCardProps {
  module: ModuleNode;
  onChangeModule: (next: ModuleNode | null) => void;
  onDeleteModule: () => void;
  onChangeSub: (subId: string, next: SubModuleNode | null) => void;
  onAddSub: () => void;
  onChangeFn: (subId: string, fnId: string, next: FunctionNode | null) => void;
  onAddFn: (subId: string) => void;
}

const ModuleCard: React.FC<ModuleCardProps> = ({
  module,
  onChangeModule,
  onDeleteModule,
  onChangeSub,
  onAddSub,
  onChangeFn,
  onAddFn,
}) => {
  const subList = Object.values(module.subModules ?? {});
  return (
    <Card
      size="small"
      title={
        <Space>
          <Tag color="purple">模块 {module.id}</Tag>
          <Input
            size="small"
            style={{ width: 280 }}
            value={module.moduleName}
            onChange={(e) => onChangeModule({ ...module, moduleName: e.target.value })}
            placeholder="模块名（业务领域/场景）"
          />
        </Space>
      }
      extra={
        <Popconfirm title="确认删除该模块及其所有子节点？" onConfirm={onDeleteModule}>
          <Button size="small" danger type="text" icon={<DeleteOutlined />}>
            删除模块
          </Button>
        </Popconfirm>
      }
      style={{ marginBottom: 12 }}
    >
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Space wrap>
          <Text type="secondary">关键词（仅名词）</Text>
          <Select
            mode="tags"
            size="small"
            style={{ minWidth: 360 }}
            placeholder="3-5 个名词"
            value={module.keywords ?? []}
            onChange={(keywords) => onChangeModule({ ...module, keywords })}
          />
        </Space>

        <Divider style={{ margin: '8px 0' }} />

        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Text strong>子模块 ({subList.length})</Text>
          <Button size="small" icon={<PlusOutlined />} onClick={onAddSub}>
            新增子模块
          </Button>
        </Space>

        {subList.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚无子模块" />}

        {subList.map((sub) => (
          <SubModuleCard
            key={sub.id}
            sub={sub}
            onChange={(next) => onChangeSub(sub.id, next)}
            onDelete={() => onChangeSub(sub.id, null)}
            onChangeFn={(fnId, next) => onChangeFn(sub.id, fnId, next)}
            onAddFn={() => onAddFn(sub.id)}
          />
        ))}
      </Space>
    </Card>
  );
};

interface SubModuleCardProps {
  sub: SubModuleNode;
  onChange: (next: SubModuleNode | null) => void;
  onDelete: () => void;
  onChangeFn: (fnId: string, next: FunctionNode | null) => void;
  onAddFn: () => void;
}

const SubModuleCard: React.FC<SubModuleCardProps> = ({ sub, onChange, onDelete, onChangeFn, onAddFn }) => {
  const fnList = Object.values(sub.functions ?? {});
  return (
    <Card
      size="small"
      type="inner"
      title={
        <Space>
          <Tag color="cyan">子模块 {sub.id}</Tag>
          <Input
            size="small"
            style={{ width: 240 }}
            value={sub.subModuleName}
            onChange={(e) => onChange({ ...sub, subModuleName: e.target.value })}
            placeholder="子模块名（具体业务功能）"
          />
        </Space>
      }
      extra={
        <Popconfirm title="确认删除该子模块及其所有功能？" onConfirm={onDelete}>
          <Button size="small" danger type="text" icon={<DeleteOutlined />}>
            删除子模块
          </Button>
        </Popconfirm>
      }
    >
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Space wrap>
          <Text type="secondary">关键词</Text>
          <Select
            mode="tags"
            size="small"
            style={{ minWidth: 320 }}
            placeholder="3-5 个关键词"
            value={sub.keywords ?? []}
            onChange={(keywords) => onChange({ ...sub, keywords })}
          />
        </Space>

        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Text strong>功能 ({fnList.length})</Text>
          <Button size="small" icon={<PlusOutlined />} onClick={onAddFn}>
            新增功能
          </Button>
        </Space>

        {fnList.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚无功能" />}

        {fnList.map((fn) => (
          <FunctionRow
            key={fn.id}
            fn={fn}
            onChange={(next) => onChangeFn(fn.id, next)}
            onDelete={() => onChangeFn(fn.id, null)}
          />
        ))}
      </Space>
    </Card>
  );
};

interface FunctionRowProps {
  fn: FunctionNode;
  onChange: (next: FunctionNode | null) => void;
  onDelete: () => void;
}

const FunctionRow: React.FC<FunctionRowProps> = ({ fn, onChange, onDelete }) => {
  return (
    <Card size="small" type="inner" style={{ background: '#fafafa' }}>
      <Space direction="vertical" size={8} style={{ width: '100%' }}>
        <Space wrap>
          <Tag color="green">功能 {fn.id}</Tag>
          <Input
            size="small"
            style={{ width: 280 }}
            value={fn.functionName}
            onChange={(e) => onChange({ ...fn, functionName: e.target.value })}
            placeholder="业务功能名（动词短语，如「白名单查询」）"
          />
          <Popconfirm title="确认删除该功能节点？" onConfirm={onDelete}>
            <Button size="small" danger type="text" icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>

        <Tooltip title="入口类全限定名集合；与其它字段一起落表 ci_module_hierarchy.class_paths，重启不会丢失。仅在提示词中被剥离，不会随提示词发往大模型。">
          <Select
            mode="tags"
            size="small"
            style={{ width: '100%' }}
            placeholder="入口类路径（如 com.example.Controller，可输入后回车添加）"
            value={fn.classPaths ?? []}
            onChange={(classPaths) => onChange({ ...fn, classPaths })}
          />
        </Tooltip>
      </Space>
    </Card>
  );
};

export default ModuleHierarchyEditor;