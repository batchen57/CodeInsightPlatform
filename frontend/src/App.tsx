import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';

function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#356AC3',
          colorInfo: '#0E7490',
          colorSuccess: '#10B981',
          colorWarning: '#D89A2F',
          colorError: '#DC2626',
          colorText: '#121826',
          colorTextSecondary: '#64748B',
          colorBgLayout: '#F5F7FB',
          colorBgContainer: '#FFFFFF',
          colorBorder: '#E2E8F0',
          colorBorderSecondary: '#EEF2F7',
          borderRadius: 6,
          borderRadiusLG: 8,
          fontFamily:
            'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif',
          fontSize: 13,
          controlHeight: 32,
          wireframe: false,
        },
        components: {
          Layout: {
            headerBg: 'rgba(255, 255, 255, 0.94)',
            siderBg: '#FFFFFF',
          },
          Card: {
            headerFontSize: 14,
            headerFontSizeSM: 13,
          },
          Table: {
            headerBg: '#F8FAFC',
            headerColor: '#64748B',
            rowHoverBg: '#F8FAFC',
            cellPaddingBlock: 12,
            cellPaddingInline: 16,
          },
          Menu: {
            itemBorderRadius: 6,
            itemHeight: 40,
          },
          Button: {
            controlHeight: 32,
            fontWeight: 500,
          },
          Input: {
            controlHeight: 32,
          },
          Select: {
            controlHeight: 32,
          },
          Modal: {
            borderRadiusLG: 10,
          },
        },
      }}
    >
      <RouterProvider router={router} />
    </ConfigProvider>
  );
}

export default App;
