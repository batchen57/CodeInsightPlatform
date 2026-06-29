import { useEffect, useRef } from 'react';

interface CodeRainProps {
  /** 自定义颜色 (默认主蓝) */
  color?: string;
  /** 不透明度 0-1 */
  opacity?: number;
  /** 字体大小 px */
  fontSize?: number;
  /** 下落速度倍率 */
  speed?: number;
}

// 代码相关字符集(去掉了和代码无关的字符,只保留代码里常见的)
const CODE_CHARS = (
  '{}()[]<>;:=+-*/%!&|?.\\' +
  '0123456789' +
  'abcdefghijklmnopqrstuvwxyz' +
  'ABCDEFGHIJKLMNOPQRSTUVWXYZ' +
  '_$@#'
).split('');

// 关键词:在底部偶尔显示成稍大、稍亮的「代码词」,增强识别度
const KEYWORDS = [
  'const', 'let', 'var', 'function', 'return', 'import', 'export',
  'class', 'interface', 'type', 'enum', 'async', 'await', 'yield',
  'new', 'this', 'super', 'extends', 'implements', 'public', 'private',
  'protected', 'static', 'void', 'null', 'undefined', 'true', 'false',
  'if', 'else', 'for', 'while', 'switch', 'case', 'break', 'continue',
  'try', 'catch', 'finally', 'throw', 'return', 'in', 'of', 'typeof',
  'instanceof', 'void', 'delete',
];

/**
 * CodeRain 组件 — 代码雨背景动效
 * 极简 Matrix 风格,但只用代码相关字符(大括号/运算符/字母数字)
 * 性能:canvas + requestAnimationFrame,40-80 列,自动响应 resize
 * 减弱动效:prefers-reduced-motion 下不渲染
 */
const CodeRain: React.FC<CodeRainProps> = ({
  // 默认冷灰蓝,跟登录卡片的浅蓝玻璃感更搭,不抢戏
  color = '120, 138, 168',
  opacity = 0.18,
  fontSize = 14,
  speed = 1,
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    // 减弱动效偏好:不渲染
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let width = window.innerWidth;
    let height = window.innerHeight;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);

    const setSize = () => {
      width = window.innerWidth;
      height = window.innerHeight;
      canvas.width = width * dpr;
      canvas.height = height * dpr;
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;
      ctx.scale(dpr, dpr);
    };
    setSize();

    const columnWidth = Math.max(10, fontSize);
    const columns = Math.ceil(width / columnWidth);

    // 每列的当前 y 位置(从屏幕上方开始)
    const drops: number[] = new Array(columns).fill(0).map(() => -Math.random() * 30);
    // 每列的速度倍率
    const speeds: number[] = new Array(columns).fill(0).map(() => 0.6 + Math.random() * 0.8);
    // 每列的字符(决定是否显示 keyword)
    const useKeyword: boolean[] = new Array(columns).fill(0).map(() => Math.random() < 0.08);

    let animationId = 0;
    let lastTime = performance.now();

    const draw = (now: number) => {
      const delta = (now - lastTime) / 16; // 归一化到 60fps 基准
      lastTime = now;

      // 拖尾:每帧用极淡的背景色覆盖,旧的字符自然褪色(系数调大,褪得更快)
      ctx.fillStyle = 'rgba(246, 247, 251, 0.1)';
      ctx.fillRect(0, 0, width, height);

      ctx.font = `${fontSize}px "JetBrains Mono", "SF Mono", ui-monospace, monospace`;
      ctx.textBaseline = 'top';

      for (let i = 0; i < columns; i++) {
        const x = i * columnWidth;
        const y = drops[i] * fontSize;

        // 决定这一帧画什么:偶尔画 keyword,大部分是单字符
        const isKeyword = useKeyword[i] && Math.floor(drops[i]) % 8 === 0;
        let text: string;
        let alpha: number;

        if (isKeyword) {
          text = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
          alpha = 0.45;
        } else {
          text = CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)];
          // 字符头部(刚出现的位置)最亮,尾部(老位置)渐暗 — 但整体调低避免抢戏
          alpha = 0.12 + 0.22 * Math.min(1, y / (height * 0.4));
        }

        ctx.fillStyle = `rgba(${color}, ${alpha * (opacity / 0.18)})`;
        ctx.fillText(text, x, y);

        // 随机从顶部重置(有的列下落快,有的慢)
        if (y > height && Math.random() > 0.975) {
          drops[i] = -Math.random() * 10;
          speeds[i] = 0.6 + Math.random() * 0.8;
          useKeyword[i] = Math.random() < 0.08;
        }

        drops[i] += speeds[i] * speed * (delta * 0.5 + 0.5);
      }

      animationId = requestAnimationFrame(draw);
    };

    animationId = requestAnimationFrame((t) => {
      lastTime = t;
      draw(t);
    });

    const handleResize = () => {
      setSize();
      // 重置列数
      const newColumns = Math.ceil(width / columnWidth);
      drops.length = newColumns;
      speeds.length = newColumns;
      useKeyword.length = newColumns;
      for (let i = 0; i < newColumns; i++) {
        if (drops[i] === undefined) drops[i] = -Math.random() * 30;
        if (speeds[i] === undefined) speeds[i] = 0.6 + Math.random() * 0.8;
        if (useKeyword[i] === undefined) useKeyword[i] = Math.random() < 0.08;
      }
    };
    window.addEventListener('resize', handleResize);

    return () => {
      cancelAnimationFrame(animationId);
      window.removeEventListener('resize', handleResize);
    };
  }, [color, opacity, fontSize, speed]);

  return <canvas ref={canvasRef} className="ci-code-rain" aria-hidden="true" />;
};

export default CodeRain;
