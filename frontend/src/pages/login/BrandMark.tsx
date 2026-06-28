import React from 'react';

type BrandMarkProps = {
  size?: number;
  className?: string;
};

/**
 * 平台品牌标识
 * 抽象的 `</>` 笔触 + 一个知识节点，强调「代码 ↔ 知识」的双向链路。
 * 用 linearGradient 复用登录页主色，避免硬编码。
 */
const BrandMark: React.FC<BrandMarkProps> = ({ size = 40, className }) => {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 40 40"
      role="img"
      aria-label="Code Insight Platform"
    >
      <defs>
        <linearGradient id="brandGradient" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#6c72f0" />
          <stop offset="55%" stopColor="#4d72dc" />
          <stop offset="100%" stopColor="#44b8cf" />
        </linearGradient>
        <linearGradient id="brandInner" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#ffffff" stopOpacity="0.95" />
          <stop offset="100%" stopColor="#ffffff" stopOpacity="0.78" />
        </linearGradient>
      </defs>
      <rect
        x="0"
        y="0"
        width="40"
        height="40"
        rx="10"
        fill="url(#brandGradient)"
      />
      <rect
        x="0.5"
        y="0.5"
        width="39"
        height="39"
        rx="9.5"
        fill="none"
        stroke="rgba(255,255,255,0.18)"
      />
      {/* < / 笔触 */}
      <path
        d="M13 16 L8 20 L13 24"
        fill="none"
        stroke="url(#brandInner)"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M27 16 L32 20 L27 24"
        fill="none"
        stroke="url(#brandInner)"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {/* / 中间笔触 */}
      <path
        d="M22 13 L18 27"
        fill="none"
        stroke="url(#brandInner)"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
      {/* 右上角知识节点 */}
      <circle cx="30" cy="10" r="2.4" fill="url(#brandInner)" />
      <circle cx="30" cy="10" r="3.6" fill="none" stroke="url(#brandInner)" strokeOpacity="0.45" strokeWidth="0.8" />
    </svg>
  );
};

export default BrandMark;
