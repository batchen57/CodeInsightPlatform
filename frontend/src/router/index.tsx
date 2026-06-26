import { createHashRouter } from 'react-router-dom';
import BasicLayout from '../layouts/BasicLayout';
import Dashboard from '../pages/dashboard';
import Systems from '../pages/systems';
import Prompts from '../pages/prompts';
import Tasks from '../pages/tasks';
import TaskDetail from '../pages/tasks/detail';
import Drafts from '../pages/drafts';
import Push from '../pages/push';
import TokenAudit from '../pages/token-audit';
import Logs from '../pages/logs';
import ModelConfig from '../pages/model/config';

export const router = createHashRouter([
  {
    path: '/',
    element: <BasicLayout />,
    children: [
      {
        path: '',
        element: <Dashboard />,
      },
      {
        path: 'systems',
        element: <Systems />,
      },
      {
        path: 'models',
        element: <ModelConfig />,
      },
      {
        path: 'prompts',
        element: <Prompts />,
      },

      {
        path: 'tasks',
        element: <Tasks />,
      },
      {
        path: 'tasks/:id',
        element: <TaskDetail />,
      },
      {
        path: 'drafts',
        element: <Drafts />,
      },
      {
        path: 'push',
        element: <Push />,
      },
      {
        path: 'audit',
        element: <TokenAudit />,
      },
      {
        path: 'logs',
        element: <Logs />,
      },
    ],
  },
]);
