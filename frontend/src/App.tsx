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
          colorPrimary: '#5258e8',
          colorInfo: '#2563eb',
          colorSuccess: '#2e9a62',
          colorWarning: '#b7791f',
          colorError: '#c4485d',
          colorText: '#171a23',
          colorTextSecondary: '#697386',
          colorBgLayout: '#f5f6fa',
          colorBgContainer: '#ffffff',
          colorBorder: '#e2e5ec',
          colorBorderSecondary: '#eceef3',
          borderRadius: 9,
          borderRadiusLG: 14,
          fontFamily:
            'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif',
          fontSize: 14,
          controlHeight: 38,
          wireframe: false,
        },
        components: {
          Layout: {
            headerBg: 'rgba(255, 255, 255, 0.94)',
            siderBg: '#11141d',
          },
          Card: {
            headerFontSize: 14,
            headerFontSizeSM: 13,
          },
          Table: {
            headerBg: '#f8f9fb',
            headerColor: '#596274',
            rowHoverBg: '#f7f8ff',
            cellPaddingBlock: 15,
            cellPaddingInline: 18,
          },
          Menu: {
            itemBorderRadius: 9,
            itemHeight: 44,
          },
          Button: {
            controlHeight: 38,
            fontWeight: 600,
          },
          Input: {
            controlHeight: 38,
          },
          Select: {
            controlHeight: 38,
          },
          Modal: {
            borderRadiusLG: 14,
          },
        },
      }}
    >
      <RouterProvider router={router} />
    </ConfigProvider>
  );
}

export default App;
