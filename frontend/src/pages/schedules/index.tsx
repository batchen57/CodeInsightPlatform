import { Navigate } from 'react-router-dom';

/**
 * 旧 /schedules 入口已迁移到 /tasks/jobs；
 * 本组件仅做重定向，避免外部链接 404。
 */
const SchedulesRedirect = () => <Navigate to="/tasks/jobs" replace />;

export default SchedulesRedirect;
