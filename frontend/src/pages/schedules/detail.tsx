import { Navigate, useParams } from 'react-router-dom';

/**
 * 旧 /schedules/:id 入口已迁移到 /tasks/jobs/:id；
 * 本组件仅做重定向，避免外部链接 404。
 */
const ScheduleDetailRedirect = () => {
  const { id } = useParams<{ id: string }>();
  return <Navigate to={`/tasks/jobs/${id}`} replace />;
};

export default ScheduleDetailRedirect;
