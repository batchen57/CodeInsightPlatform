import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Card, Space, Spin, Tooltip, Tree, Typography } from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  FolderOpenOutlined,
  ImportOutlined,
  PushpinFilled,
  PushpinOutlined,
} from '@ant-design/icons';

const { Text } = Typography;

const DEFAULT_WIDTH = 296;
const DEFAULT_MIN_TOP = 120;
const MIN_WIDTH = 240;
const MIN_HEIGHT = 280;
const MAX_WIDTH = 560;
const MAX_HEIGHT = 900;

export interface DraftModuleDirectoryProps {
  visible: boolean;
  pinned: boolean;
  loading: boolean;
  leafCount: number;
  treeData: DataNode[];
  selectedDraftId: number | null;
  onSelectDraft: (draftId: number) => void;
  onPinnedChange: (pinned: boolean) => void;
  onResetDock: () => void;
  onMouseEnter?: () => void;
  onMouseLeave?: () => void;
  fullscreen?: boolean;
}

const DraftModuleDirectory: React.FC<DraftModuleDirectoryProps> = ({
  visible,
  pinned,
  loading,
  leafCount,
  treeData,
  selectedDraftId,
  onSelectDraft,
  onPinnedChange,
  onResetDock,
  onMouseEnter,
  onMouseLeave,
  fullscreen = false,
}) => {
  const panelRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ width: DEFAULT_WIDTH, height: fullscreen ? 520 : 480 });
  const [position, setPosition] = useState({ x: 0, y: DEFAULT_MIN_TOP });
  const [floating, setFloating] = useState(false);
  const dragRef = useRef<{ startX: number; startY: number; originX: number; originY: number } | null>(null);
  const resizeRef = useRef<{ startX: number; startY: number; originW: number; originH: number } | null>(null);

  useEffect(() => {
    if (!pinned) {
      setFloating(false);
    }
  }, [pinned]);

  const handleTogglePin = () => {
    const next = !pinned;
    onPinnedChange(next);
    if (!next) {
      setFloating(false);
      setPosition({ x: 0, y: DEFAULT_MIN_TOP });
    }
  };

  const handleResetDock = () => {
    setFloating(false);
    setPosition({ x: 0, y: DEFAULT_MIN_TOP });
    setSize({ width: DEFAULT_WIDTH, height: fullscreen ? 520 : 480 });
    onResetDock();
  };

  const onHeaderMouseDown = useCallback(
    (event: React.MouseEvent) => {
      if (!pinned) return;
      if ((event.target as HTMLElement).closest('button')) return;
      event.preventDefault();
      const rect = panelRef.current?.getBoundingClientRect();
      const originX = floating ? position.x : (rect?.left ?? 0);
      const originY = floating ? position.y : (rect?.top ?? DEFAULT_MIN_TOP);
      if (!floating) {
        setFloating(true);
        setPosition({ x: originX, y: originY });
      }
      dragRef.current = {
        startX: event.clientX,
        startY: event.clientY,
        originX,
        originY,
      };

      const onMove = (ev: MouseEvent) => {
        if (!dragRef.current) return;
        const dx = ev.clientX - dragRef.current.startX;
        const dy = ev.clientY - dragRef.current.startY;
        setPosition({
          x: Math.max(8, dragRef.current.originX + dx),
          y: Math.max(64, dragRef.current.originY + dy),
        });
      };
      const onUp = () => {
        dragRef.current = null;
        window.removeEventListener('mousemove', onMove);
        window.removeEventListener('mouseup', onUp);
      };
      window.addEventListener('mousemove', onMove);
      window.addEventListener('mouseup', onUp);
    },
    [floating, pinned, position.x, position.y],
  );

  const onResizeMouseDown = useCallback(
    (event: React.MouseEvent) => {
      event.preventDefault();
      event.stopPropagation();
      resizeRef.current = {
        startX: event.clientX,
        startY: event.clientY,
        originW: size.width,
        originH: size.height,
      };

      const onMove = (ev: MouseEvent) => {
        if (!resizeRef.current) return;
        const dw = ev.clientX - resizeRef.current.startX;
        const dh = ev.clientY - resizeRef.current.startY;
        setSize({
          width: Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, resizeRef.current.originW + dw)),
          height: Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, resizeRef.current.originH + dh)),
        });
      };
      const onUp = () => {
        resizeRef.current = null;
        window.removeEventListener('mousemove', onMove);
        window.removeEventListener('mouseup', onUp);
      };
      window.addEventListener('mousemove', onMove);
      window.addEventListener('mouseup', onUp);
    },
    [size.height, size.width],
  );

  const selectedKeys = useMemoSelectedKeys(treeData, selectedDraftId);

  const panelStyle: React.CSSProperties = floating
    ? {
        position: 'fixed',
        left: position.x,
        top: position.y,
        width: size.width,
        height: size.height,
        zIndex: fullscreen ? 1200 : 1000,
      }
    : {
        width: size.width,
        height: size.height,
      };

  return (
    <div
      ref={panelRef}
      className={[
        'ci-module-drawer',
        visible ? 'is-open' : '',
        pinned ? 'is-pinned' : '',
        floating ? 'is-floating' : '',
        fullscreen ? 'is-fullscreen' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      style={panelStyle}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
    >
      <Card
        size="small"
        className="ci-module-drawer-card"
        title={
          <div
            className={`ci-module-drawer-head ${pinned ? 'is-draggable' : ''}`}
            onMouseDown={onHeaderMouseDown}
          >
            <Space size={6}>
              <span className="ci-review-title-icon" style={{ width: 26, height: 26, fontSize: 13, borderRadius: 7 }}>
                <FolderOpenOutlined />
              </span>
              <span>模块目录</span>
              <span className="ci-status-pill" style={{ height: 19, fontSize: 10 }}>
                <span className="ci-status-pill-dot" />
                {leafCount} 个功能
              </span>
            </Space>
          </div>
        }
        extra={
          <Space size={2}>
            <Tooltip title="放回左侧并收起">
              <Button size="small" type="text" icon={<ImportOutlined />} onClick={handleResetDock} />
            </Tooltip>
            <Tooltip title={pinned ? '取消固定（结束拖拽）' : '固定后可拖拽到任意位置'}>
              <Button
                size="small"
                type="text"
                icon={pinned ? <PushpinFilled style={{ color: '#5258e8' }} /> : <PushpinOutlined />}
                onClick={handleTogglePin}
              />
            </Tooltip>
          </Space>
        }
      >
        <Spin spinning={loading} size="small">
          <div className="ci-module-drawer-tree-wrap">
            {treeData.length > 0 ? (
              <Tree
                showLine={{ showLeafIcon: false }}
                blockNode
                defaultExpandAll
                treeData={treeData}
                selectedKeys={selectedKeys}
                onSelect={(_keys, info) => {
                  const draftId = (info.node as DataNode & { draftId?: number }).draftId;
                  if (draftId != null) {
                    onSelectDraft(draftId);
                  }
                }}
              />
            ) : (
              <div className="ci-info-empty" style={{ padding: '24px 0 20px' }}>
                <strong>该工作区下暂无目录</strong>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  等待任务生成模块层级与草稿
                </Text>
              </div>
            )}
          </div>
        </Spin>
        <button
          type="button"
          className="ci-module-drawer-resize-handle"
          aria-label="调整模块目录大小"
          onMouseDown={onResizeMouseDown}
        />
      </Card>
    </div>
  );
};

function useMemoSelectedKeys(treeData: DataNode[], selectedDraftId: number | null): string[] {
  const keys: string[] = [];
  if (selectedDraftId == null) return keys;
  const walk = (nodes: DataNode[]) => {
    for (const n of nodes) {
      const draftId = (n as DataNode & { draftId?: number }).draftId;
      if (draftId === selectedDraftId && n.key != null) {
        keys.push(String(n.key));
      }
      if (n.children?.length) walk(n.children);
    }
  };
  walk(treeData);
  return keys;
}

export default DraftModuleDirectory;
