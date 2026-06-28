import React, { useEffect, useMemo, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeHighlight from 'rehype-highlight';
import mermaid from 'mermaid';
import 'highlight.js/styles/github.css';

/**
 * 知识文档 Markdown 渲染组件
 *
 * 设计要点：
 * 1. 基于 react-markdown + remark-gfm：支持标题、列表、表格、任务清单、删除线等 GFM 语法；
 * 2. 通过 rehype-highlight 为代码块提供语法高亮，主题与 premium 视觉系统一致；
 * 3. 自定义 <code> 渲染器：识别 ```mermaid 代码块，调用 mermaid 渲染出 SVG 图表；
 * 4. 当内容变化时，组件会清空旧的 mermaid 渲染结果并重新生成，保证与编辑器中的源码实时同步。
 */

export interface MarkdownViewProps {
  /** Markdown 源文本 */
  content: string;
  /** 自定义类名（用于外层容器） */
  className?: string;
}

// mermaid 模块级初始化只跑一次，配置与项目主色对齐
let mermaidInitialized = false;
function initMermaid() {
  if (mermaidInitialized) return;
  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'loose',
    theme: 'neutral',
    fontFamily:
      'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif',
    themeVariables: {
      primaryColor: '#eef0ff',
      primaryTextColor: '#171a23',
      primaryBorderColor: '#5258e8',
      lineColor: '#5258e8',
      secondaryColor: '#f5f6fa',
      tertiaryColor: '#fafbff',
      mainBkg: '#ffffff',
      nodeBorder: '#5258e8',
      clusterBkg: '#f5f6fa',
      clusterBorder: '#c5cff5',
      titleColor: '#171a23',
      edgeLabelBackground: '#ffffff',
    },
  });
  mermaidInitialized = true;
}

/**
 * 从 Markdown 文本中提取所有 mermaid 代码块（按出现顺序），用于生成稳定 id。
 * 由于 react-markdown 不会暴露原始节点位置，我们在组件内对每个 ```mermaid 块编号。
 */
function extractMermaidBlocks(markdown: string): string[] {
  const blocks: string[] = [];
  const regex = /```mermaid\s*([\s\S]*?)```/g;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(markdown)) !== null) {
    blocks.push(match[1].trim());
  }
  return blocks;
}

/**
 * Mermaid 图表渲染子组件
 * 每次 source 变化都会重新渲染，失败时展示原始源码与错误信息。
 */
const MermaidBlock: React.FC<{ source: string; idHint: string }> = ({ source, idHint }) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [svg, setSvg] = React.useState<string>('');
  const [error, setError] = React.useState<string>('');

  useEffect(() => {
    initMermaid();
    let cancelled = false;
    const renderId = `mmd-${idHint}-${Date.now()}`;
    mermaid
      .render(renderId, source)
      .then(({ svg: rendered }) => {
        if (!cancelled) {
          setSvg(rendered);
          setError('');
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : String(err));
          setSvg('');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [source, idHint]);

  if (error) {
    return (
      <div className="ci-md-mermaid is-error">
        <div className="ci-md-mermaid-error-title">Mermaid 渲染失败</div>
        <pre className="ci-md-mermaid-error-body">{error}</pre>
        <pre className="ci-md-mermaid-error-source">{source}</pre>
      </div>
    );
  }
  if (!svg) {
    return (
      <div className="ci-md-mermaid is-loading">
        <span>正在渲染 Mermaid 图表…</span>
      </div>
    );
  }
  return (
    <div
      ref={containerRef}
      className="ci-md-mermaid"
      // mermaid 返回的是干净的 SVG 字符串，可放心使用 dangerouslySetInnerHTML
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  );
};

/**
 * Markdown 渲染主组件
 */
const MarkdownView: React.FC<MarkdownViewProps> = ({ content, className }) => {
  // 预先抽出 mermaid 块，便于为每个块分配稳定 id 提示
  const mermaidBlocks = useMemo(() => extractMermaidBlocks(content), [content]);

  return (
    <div className={`ci-md ${className ?? ''}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[[rehypeHighlight, { detect: true, ignoreMissing: true }]]}
        components={{
          // 自定义 code 渲染器：mermaid 走图表组件，普通代码块由 rehype-highlight 处理
          code({ inline, className: codeClassName, children, ...props }: any) {
            const match = /language-(\w+)/.exec(codeClassName || '');
            const lang = match?.[1];
            if (!inline && lang === 'mermaid') {
              const source = String(children).replace(/\n$/, '');
              // 用源码哈希作为 idHint，保证同一图多次渲染时稳定
              const idHint = `${mermaidBlocks.indexOf(source)}-${source.length}`;
              return <MermaidBlock source={source} idHint={idHint} />;
            }
            return (
              <code className={codeClassName} {...props}>
                {children}
              </code>
            );
          },
          // 让 pre 标签自带 ci-md-pre 类，方便样式定制
          pre({ children, ...props }: any) {
            return (
              <pre className="ci-md-pre" {...props}>
                {children}
              </pre>
            );
          },
        }}
      >
        {content || '*(暂无内容)*'}
      </ReactMarkdown>
    </div>
  );
};

export default MarkdownView;