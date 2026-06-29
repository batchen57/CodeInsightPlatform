import { create } from 'zustand';

/**
 * 多页签导航状态：每个 sidebar 菜单点击都会新增一个 tab（同一路径可同时存在多个），
 * 与浏览器/IDE 的 tab 行为一致。用户可在多个 tab 之间切换，不会丢失之前打开的页面状态。
 *
 * 工作台（/）刻意不放入 tab 栏（由 BasicLayout 顶栏 logo 直接跳回）。
 */
export interface OpenTab {
  /** 全局唯一 id（同一 path 可开多个 tab） */
  id: string;
  /** 路由路径 */
  path: string;
  /** 标签显示文案 */
  title: string;
  /** 是否可关闭；非可关闭的由系统保留 */
  closable: boolean;
  /** 打开时间（用于 LRU 淘汰） */
  openedAt: number;
}

interface TabsState {
  tabs: OpenTab[];
  activeId: string | null;

  /**
   * 总是新增一个 tab。如果同 path 已存在则创建新的（id 不同），
   * 除非 caller 显式要求去重（dedupe=true）。
   */
  addTab: (input: { path: string; title: string; closable?: boolean; dedupe?: boolean }) => string;

  /** 激活指定 tab（仅切 active，不导航） */
  setActive: (id: string) => void;

  /** 关闭指定 tab；若关闭的是 active，则由 caller 自行决定跳到哪个 path */
  removeTab: (id: string) => void;

  /** 关闭除指定 id 之外的所有可关闭 tab */
  closeOthers: (id: string) => void;

  /** 关闭所有可关闭 tab */
  closeAll: () => void;
}

const MAX_TABS = 15;

export const useTabsStore = create<TabsState>((set, get) => ({
  tabs: [],
  activeId: null,

  addTab: ({ path, title, closable = true, dedupe = false }) => {
    // 去重：若已存在同 path 的 tab 且要求 dedupe，激活现有 tab
    if (dedupe) {
      const existing = get().tabs.find((t) => t.path === path);
      if (existing) {
        set({ activeId: existing.id });
        return existing.id;
      }
    }

    const id = `${path}#${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
    const newTab: OpenTab = { id, path, title, closable, openedAt: Date.now() };

    set((state) => {
      let nextTabs = [...state.tabs, newTab];
      // 超过 MAX_TABS 时，淘汰最早打开且可关闭的 tab
      if (nextTabs.length > MAX_TABS) {
        const closableIdx = nextTabs
          .map((t, i) => ({ t, i }))
          .filter(({ t }) => t.closable)
          .sort((a, b) => a.t.openedAt - b.t.openedAt);
        const dropCount = nextTabs.length - MAX_TABS;
        const dropIdxs = new Set(closableIdx.slice(0, dropCount).map(({ i }) => i));
        nextTabs = nextTabs.filter((_, i) => !dropIdxs.has(i));
      }
      return { tabs: nextTabs, activeId: id };
    });
    return id;
  },

  setActive: (id) => set({ activeId: id }),

  removeTab: (id) => {
    set((state) => {
      const idx = state.tabs.findIndex((t) => t.id === id);
      if (idx < 0) return state;
      const wasActive = state.activeId === id;
      const nextTabs = state.tabs.filter((t) => t.id !== id);
      let nextActive = state.activeId;
      if (wasActive) {
        // 优先选右边的，再选左边的
        nextActive = nextTabs[idx]?.id ?? nextTabs[idx - 1]?.id ?? null;
      }
      return { tabs: nextTabs, activeId: nextActive };
    });
  },

  closeOthers: (id) => {
    set((state) => ({
      tabs: state.tabs.filter((t) => t.id === id || !t.closable),
      activeId: id,
    }));
  },

  closeAll: () => {
    set((state) => ({
      tabs: state.tabs.filter((t) => !t.closable),
      activeId: state.tabs.find((t) => !t.closable)?.id ?? null,
    }));
  },
}));
