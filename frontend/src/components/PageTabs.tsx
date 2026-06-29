import React, { useCallback, useEffect, useRef } from 'react';
import { Tabs, Dropdown, Empty } from 'antd';
import type { MenuProps } from 'antd';
import { CloseOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTabsStore, type OpenTab } from '../stores/tabs';
import '../styles/page-tabs.css';

/**
 * 多页签导航条：
 *  - 浏览器/IDE 风格：每个打开的页面都是一个 tab
 *  - 点击 tab 切换路由；点击 × 关闭；右击弹右键菜单
 *  - 始终保留至少 1 个可关闭 tab（除非全部都是不可关闭的）
 *  - 工作台 (/) 不在 tab 栏里（用户通过顶栏 logo 返回）
 */
const PageTabs: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const tabs = useTabsStore((s) => s.tabs);
  const activeId = useTabsStore((s) => s.activeId);
  const setActive = useTabsStore((s) => s.setActive);
  const removeTab = useTabsStore((s) => s.removeTab);
  const closeOthers = useTabsStore((s) => s.closeOthers);
  const closeAll = useTabsStore((s) => s.closeAll);

  /** 在 pathname 变化时（如浏览器后退/前进/直接改 URL），同步 activeId */
  const lastSyncedPath = useRef<string | null>(null);
  useEffect(() => {
    const currentPath = location.pathname;
    if (currentPath === '/') {
      // 工作台不在 tab 栏里：切到工作台时清空 activeId
      useTabsStore.setState({ activeId: null });
      lastSyncedPath.current = currentPath;
      return;
    }
    // 找到与当前路径完全匹配的 tab（多 tab 同 path 时优先选最后激活的）
    const matched = tabs.filter((t) => t.path === currentPath);
    if (matched.length > 0) {
      const target = matched[matched.length - 1];
      if (target.id !== activeId) {
        setActive(target.id);
      }
    }
    // 不在 tabs 里：用户可能直接访问 URL；什么都不做（不自动建 tab）
    lastSyncedPath.current = currentPath;
  }, [location.pathname, tabs, activeId, setActive]);

  const handleChange = useCallback(
    (id: string) => {
      const tab = tabs.find((t) => t.id === id);
      if (!tab) return;
      setActive(id);
      if (location.pathname !== tab.path) {
        navigate(tab.path);
      }
    },
    [tabs, setActive, location.pathname, navigate],
  );

  const handleEdit = useCallback(
    (
      e: React.MouseEvent | React.KeyboardEvent | string,
      action: 'add' | 'remove',
    ) => {
      if (action !== 'remove') return;
      // antd 在 type=editable-card 下 remove 的第一个参数是 key（string）
      const key = typeof e === 'string' ? e : '';
      const tab = tabs.find((t) => t.id === key);
      if (!tab) return;
      const wasActive = activeId === tab.id;
      removeTab(tab.id);
      if (wasActive) {
        const nextActiveId = useTabsStore.getState().activeId;
        if (nextActiveId) {
          const nextTab = useTabsStore.getState().tabs.find((t) => t.id === nextActiveId);
          if (nextTab) navigate(nextTab.path);
        } else {
          navigate('/');
        }
      }
    },
    [tabs, activeId, removeTab, navigate],
  );

  /** 右键菜单 */
  const getContextMenu = (tab: OpenTab): MenuProps['items'] => {
    if (!tab.closable) return [];
    return [
      {
        key: 'close-self',
        label: '关闭当前',
        onClick: () => {
          const wasActive = activeId === tab.id;
          removeTab(tab.id);
          if (wasActive) {
            const nextActiveId = useTabsStore.getState().activeId;
            if (nextActiveId) {
              const nextTab = useTabsStore.getState().tabs.find((t) => t.id === nextActiveId);
              if (nextTab) navigate(nextTab.path);
            } else {
              navigate('/');
            }
          }
        },
      },
      {
        key: 'close-others',
        label: '关闭其他',
        onClick: () => {
          closeOthers(tab.id);
          navigate(tab.path);
        },
      },
      { type: 'divider' },
      {
        key: 'close-all',
        label: '关闭全部',
        onClick: () => {
          closeAll();
          navigate('/');
        },
      },
    ];
  };

  if (tabs.length === 0) {
    // 没有 tab 时不渲染条（用户大概率在 /）
    return (
      <div className="ci-page-tabs ci-page-tabs--empty">
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={
            <span style={{ color: '#999' }}>
              点击左侧菜单打开页面（工作台不计入页签）
            </span>
          }
        />
      </div>
    );
  }

  return (
    <div className="ci-page-tabs">
      <Tabs
        type="editable-card"
        hideAdd
        size="small"
        activeKey={activeId ?? undefined}
        onChange={handleChange}
        onEdit={handleEdit}
        items={tabs.map((t) => {
          const menuItems = getContextMenu(t);
          const labelNode = (
            <span className="ci-page-tab-label">
              <span className="ci-page-tab-title">{t.title}</span>
              {t.closable && (
                <span
                  role="button"
                  aria-label="close"
                  className="ci-page-tab-close"
                  onClick={(e) => {
                    e.stopPropagation();
                    const wasActive = activeId === t.id;
                    removeTab(t.id);
                    if (wasActive) {
                      const nextId = useTabsStore.getState().activeId;
                      if (nextId) {
                        const next = useTabsStore.getState().tabs.find((x) => x.id === nextId);
                        if (next) navigate(next.path);
                      } else {
                        navigate('/');
                      }
                    }
                  }}
                  onMouseDown={(e) => e.stopPropagation()}
                >
                  <CloseOutlined />
                </span>
              )}
            </span>
          );
          const node = (menuItems?.length ?? 0) > 0 ? (
            <Dropdown menu={{ items: menuItems }} trigger={['contextMenu']}>
              {labelNode}
            </Dropdown>
          ) : (
            labelNode
          );
          return {
            key: t.id,
            label: node,
            closable: false, // 用自定义 close 按钮
          };
        })}
      />
    </div>
  );
};

export default PageTabs;
