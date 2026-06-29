import type React from 'react';
import { Form, Select } from 'antd';
import type { EntryScanConfig } from '../types';

/**
 * 入口扫描配置的轻量编辑器（折叠在卡片中）。
 * 用于 schedule / task 等表单复用同一个配置 UI。
 *
 * <p>字段语义同 {@link EntryScanConfig}：
 * <ul>
 *   <li>include 类：满足任一即视为入口</li>
 *   <li>exclude 类：满足任一即从候选中排除</li>
 * </ul>
 */
const EntryScanConfigEditor: React.FC = () => {
  return (
    <div className="ci-scan-config">
      <div className="ci-scan-config-col-title">入口识别（满足任一即视为入口）</div>
      <div className="ci-scan-config-row">
        <span className="ci-scan-config-label">注解</span>
        <Form.Item name={['entryScanConfig', 'includeAnnotations']} noStyle>
          <Select
            size="small"
            mode="tags"
            placeholder="RestController / Service / ..."
            style={{ width: '100%' }}
            tokenSeparators={[',']}
          />
        </Form.Item>
      </div>
      <div className="ci-scan-config-row">
        <span className="ci-scan-config-label">类路径</span>
        <Form.Item name={['entryScanConfig', 'includeClasspaths']} noStyle>
          <Select
            size="small"
            mode="tags"
            placeholder="com.demo.controller.**"
            style={{ width: '100%' }}
            tokenSeparators={[',']}
          />
        </Form.Item>
      </div>
      <div className="ci-scan-config-row">
        <span className="ci-scan-config-label">继承/实现</span>
        <Form.Item name={['entryScanConfig', 'includeExtends']} noStyle>
          <Select
            size="small"
            mode="tags"
            placeholder="BaseEntry / CommandLineRunner"
            style={{ width: '100%' }}
            tokenSeparators={[',']}
          />
        </Form.Item>
      </div>

      <div className="ci-scan-config-col-title" style={{ marginTop: 8 }}>
        排除规则（满足任一即从候选中排除）
      </div>
      <div className="ci-scan-config-row">
        <span className="ci-scan-config-label">类路径</span>
        <Form.Item name={['entryScanConfig', 'excludeClasspaths']} noStyle>
          <Select
            size="small"
            mode="tags"
            placeholder="*.test.*"
            style={{ width: '100%' }}
            tokenSeparators={[',']}
          />
        </Form.Item>
      </div>
      <div className="ci-scan-config-row">
        <span className="ci-scan-config-label">包路径</span>
        <Form.Item name={['entryScanConfig', 'excludePackages']} noStyle>
          <Select
            size="small"
            mode="tags"
            placeholder="com.legacy.config"
            style={{ width: '100%' }}
            tokenSeparators={[',']}
          />
        </Form.Item>
      </div>
      <div className="ci-scan-config-row">
        <span className="ci-scan-config-label">注解</span>
        <Form.Item name={['entryScanConfig', 'excludeAnnotations']} noStyle>
          <Select
            size="small"
            mode="tags"
            placeholder="Internal / Deprecated"
            style={{ width: '100%' }}
            tokenSeparators={[',']}
          />
        </Form.Item>
      </div>
    </div>
  );
};

export default EntryScanConfigEditor;