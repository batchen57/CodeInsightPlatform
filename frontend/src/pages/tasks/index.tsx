import React from 'react';
import { Tabs } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const TAB_KEYS = {
  query: '/tasks/query',
  queue: '/tasks/queue',
  jobs: '/tasks/jobs',
  dispatch: '/tasks/dispatch',
} as const;

type TabKey = keyof typeof TAB_KEYS;

const PATH_TO_KEY: Record<string, TabKey> = {
  '/tasks/query': 'query',
  '/tasks/queue': 'queue',
  '/tasks/jobs': 'jobs',
  '/tasks/dispatch': 'dispatch',
  '/tasks/jobs/new': 'jobs',
  '/tasks': 'query',
};

const TAB_ITEMS = [
  { key: 'query', label: '任务查询' },
  { key: 'queue', label: '队列' },
  { key: 'jobs', label: 'JOB配置' },
  { key: 'dispatch', label: '手动下发' },
];

/**
 * 反编译任务主入口（页签壳）
 *
 *  - 任务查询（默认） — /tasks/query
 *  - JOB配置         — /tasks/jobs (含 /new、/:id/edit、/:id)
 *  - 手动下发         — /tasks/dispatch
 *
 * 具体内容由各子路由渲染；本组件只负责页签导航与 Outlet 挂载。
 */
const Tasks: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();

  const activeKey: TabKey =
    PATH_TO_KEY[location.pathname] ??
    (location.pathname.startsWith('/tasks/jobs') ? 'jobs' : 'query');

  const handleChange = (next: string) => {
    navigate(TAB_KEYS[next as TabKey]);
  };

  return (
    <div className="ci-page ci-tasks-shell">
      <Tabs
        activeKey={activeKey}
        onChange={handleChange}
        items={TAB_ITEMS}
        tabBarStyle={{ marginBottom: 12 }}
      />
      <Outlet />
    </div>
  );
};

export default Tasks;
