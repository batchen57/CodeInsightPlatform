import React from 'react';
import { Outlet } from 'react-router-dom';

/**
 * 知识构建任务路由容器（无页签壳）
 *
 * 子页面均通过侧栏独立入口访问：
 *  - 任务查询 — /tasks/query
 *  - 任务队列 — /tasks/queue
 *  - JOB配置  — /tasks/jobs
 *  - 手动下发 — /tasks/dispatch
 */
const Tasks: React.FC = () => <Outlet />;

export default Tasks;
