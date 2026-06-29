import type { AiModel } from '../../../types';

export const getPreferredModel = (models: AiModel[]): AiModel | undefined => {
  const availableModels = models.filter((model) => model.status !== 0 && model.hasApiKey);
  return availableModels.find((model) => model.isDefault === 'true') ?? availableModels[0] ?? models.find((model) => model.status !== 0) ?? models[0];
};

export const getModelOptionDisabled = (model: AiModel): boolean => {
  return model.status === 0 || !model.hasApiKey;
};

export const formatDateTime = (value?: string): string => {
  return value ? new Date(value).toLocaleString() : '-';
};

/**
 * 从提示词模板中抽取所有占位符变量名（形如 ${var_name}）
 *  - 去重 + 按出现顺序保留首次出现的顺序
 *  - 跳过空白 / 数字开头的非法名（正则 [a-zA-Z_][a-zA-Z0-9_]*）
 *  - 返回去掉 `${}` 的纯变量名数组
 */
export const PLACEHOLDER_PATTERN = /\$\{([a-zA-Z_][a-zA-Z0-9_]*)\}/g;

export const extractPlaceholders = (template?: string): string[] => {
  if (!template) return [];
  const seen = new Set<string>();
  const order: string[] = [];
  // 每次 new 一个 RegExp，避免 lastIndex 状态污染
  const re = new RegExp(PLACEHOLDER_PATTERN.source, 'g');
  let m: RegExpExecArray | null;
  while ((m = re.exec(template)) !== null) {
    const name = m[1];
    if (!seen.has(name)) {
      seen.add(name);
      order.push(name);
    }
  }
  return order;
};

/**
 * 把模板里的 ${var} 全部替换成 vars[var](缺失则保留原样)
 * 简单 string replace,不做转义;若 vars 含 null/undefined 跳过该 key
 */
export const substitutePlaceholders = (
  template: string | undefined,
  vars: Record<string, string | undefined>,
): string => {
  if (!template) return '';
  return template.replace(PLACEHOLDER_PATTERN, (match, name: string) => {
    const v = vars[name];
    return v == null ? match : v;
  });
};

/**
 * 把 `snake_case` / `camelCase` 转成中文友好的 Label:如 class_name → Class Name
 * 仅作 UI 标签展示,后端不依赖
 */
export const formatVariableLabel = (name: string): string => {
  const withSpaces = name.replace(/[_-]+/g, ' ').replace(/([a-z])([A-Z])/g, '$1 $2');
  return withSpaces.replace(/\b\w/g, (c) => c.toUpperCase());
};

/** 已知可从 sampleCode 自动提取的变量（仅做 UI 默认值,真正取数后端再做） */
export const AUTO_EXTRACTED_VARS = new Set(['class_name', 'method_name', 'source_code']);

