import React from 'react';
import { Navigate, useParams } from 'react-router-dom';
import DraftReviewWorkspace from './workspace';

/**
 * 知识复核详情：按任务 ID 打开复核工作区。
 */
const DraftReviewDetailPage: React.FC = () => {
  const { taskId } = useParams();
  const id = Number(taskId);
  if (!Number.isFinite(id) || id <= 0) {
    return <Navigate to="/drafts" replace />;
  }
  return <DraftReviewWorkspace taskId={id} />;
};

export default DraftReviewDetailPage;
