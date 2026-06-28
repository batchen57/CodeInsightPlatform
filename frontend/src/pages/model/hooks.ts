import { useCallback, useEffect, useState } from 'react';
import { listModels } from '../../api/model';
import type { AiModel } from '../../types';

/**
 * AI 模型列表数据 hook
 * 不分页（模型数量通常 < 20 条）
 */
export function useModels() {
  const [models, setModels] = useState<AiModel[]>([]);
  const [loading, setLoading] = useState(false);

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listModels();
      setModels(data);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetch();
  }, [fetch]);

  return { models, loading, fetch, setModels };
}

/**
 * 拖拽排序 hook
 * 负责：
 *  - 维护 dragIndex / dragOverIndex / rowDraggable 三个状态
 *  - 暴露 onRow(index) 返回 Table 行级 props
 *  - 暴露 reorder(targetIndex) 计算并返回新顺序（按 sortOrder = (idx+1)*10 重新分配权重）
 *  - 暴露 reset() 在拖拽结束时清理状态
 *
 * 为什么不直接在这里发 API？API 调用要 refresh 数据 + toast，让父组件控制。
 */
export function useDragSort() {
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null);
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null);
  const [rowDraggable, setRowDraggable] = useState(false);

  const onRow = useCallback(
    (index: number) => ({
      draggable: rowDraggable,
      onDragStart: (e: React.DragEvent) => {
        setDraggedIndex(index);
        e.dataTransfer.effectAllowed = 'move';
      },
      onDragOver: (e: React.DragEvent) => {
        e.preventDefault();
        setDragOverIndex((cur) => (cur === index ? cur : index));
      },
      onDragLeave: () => setDragOverIndex(null),
      onDrop: (e: React.DragEvent) => {
        e.preventDefault();
        const target = dragOverIndex ?? index;
        setDraggedIndex(null);
        setDragOverIndex(null);
        return target;
      },
      onDragEnd: () => {
        setDraggedIndex(null);
        setDragOverIndex(null);
        setRowDraggable(false);
      },
      className: [
        'ci-model-row-draggable',
        draggedIndex === index ? 'ci-model-row-dragging' : '',
        dragOverIndex === index ? 'ci-model-row-drag-over' : '',
      ]
        .filter(Boolean)
        .join(' '),
      style: { cursor: rowDraggable ? 'grab' : 'default' },
    }),
    [draggedIndex, dragOverIndex, rowDraggable],
  );

  /** 计算拖拽后的新顺序 + 重新分配 sortOrder = (idx+1)*10 */
  const reorder = useCallback(
    (items: AiModel[], from: number, to: number): AiModel[] => {
      if (from === to || from < 0 || to < 0 || from >= items.length || to >= items.length) {
        return items;
      }
      const next = [...items];
      const [moved] = next.splice(from, 1);
      next.splice(to, 0, moved);
      return next.map((item, idx) => ({ ...item, sortOrder: (idx + 1) * 10 }));
    },
    [],
  );

  /** 行 hover 进入时启用 draggable（鼠标在拖拽把手上时） */
  const enableDrag = useCallback(() => setRowDraggable(true), []);
  const disableDrag = useCallback(() => setRowDraggable(false), []);

  return {
    onRow,
    reorder,
    draggedIndex,
    dragOverIndex,
    rowDraggable,
    enableDrag,
    disableDrag,
  };
}
