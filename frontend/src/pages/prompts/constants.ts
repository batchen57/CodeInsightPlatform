export type PromptType = 'MODULARIZE' | 'DOCUMENT_GENERATION';

export const PROMPT_TYPE_TABS: Array<{ key: PromptType; label: string; description: string }> = [
  {
    key: 'MODULARIZE',
    label: '模块提取提示词',
    description: '用于 AI_ANALYZING / MODULE_HIERARCHY 阶段，提取项目代码模块与职责。',
  },
  {
    key: 'DOCUMENT_GENERATION',
    label: '说明文档提示词',
    description: '用于 GENERATING_DOC 阶段，将分析结果组织成可复核的说明文档。',
  },
];

export const DEFAULT_PROMPTS: Record<PromptType, string> = {
  MODULARIZE: `你是一名资深代码知识分析师。

请阅读下面的 Java 代码，生成可供开发负责人复核的 Markdown 知识草稿。

类名：\${class_name}
核心方法：\${method_name}
源代码：
\${source_code}

输出章节：
1. 职责概述
2. 核心流程
3. 重要依赖
4. 待复核事项`,
  DOCUMENT_GENERATION: `你是一名资深技术文档工程师。

请基于下面的模块分析材料，生成面向研发团队的说明文档草稿。

类名：\${class_name}
核心方法：\${method_name}
分析材料：
\${source_code}

输出章节：
1. 模块定位
2. 业务流程说明
3. 接口与依赖关系
4. 运维与复核建议`,
};

export const DEFAULT_SAMPLE_CODE = `public class OrderService {
  public void createOrder(Order order) {
    checkInventory(order.getItemId());
    orderMapper.insert(order);
  }
}`;
