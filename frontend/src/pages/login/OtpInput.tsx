import React, { useCallback, useRef } from 'react';

type OtpInputProps = {
  /**
   * 当前值。Form.Item 会通过 cloneElement 自动注入；独立使用时可显式传。
   * 不传时按空串处理，避免 undefined[i] 报错。
   */
  value?: string;
  /**
   * 值变化回调。Form.Item 会通过 cloneElement 自动注入；独立使用时可显式传。
   * 不传时为 no-op，便于在表单外独立使用。
   */
  onChange?: (next: string) => void;
  onComplete?: () => void;
  length?: number;
  disabled?: boolean;
  autoFocus?: boolean;
};

/**
 * 6 位平安令牌输入控件
 * - 自动跳格 / 退格回退
 * - 支持粘贴（自动拆分到 6 格）
 * - 满 6 位触发 onComplete（用于自动提交）
 */
const OtpInput: React.FC<OtpInputProps> = ({
  value = '',
  onChange,
  onComplete,
  length = 6,
  disabled = false,
  autoFocus = true,
}) => {
  const refs = useRef<Array<HTMLInputElement | null>>([]);

  const digits = useMemoDigits(value, length);

  const setDigit = useCallback(
    (index: number, char: string) => {
      const next = digits.slice();
      next[index] = char;
      onChange?.(next.join(''));
      if (char && index < length - 1) {
        refs.current[index + 1]?.focus();
        refs.current[index + 1]?.select();
      }
      if (next.every((d) => d !== '') && onComplete) {
        onComplete();
      }
    },
    [digits, length, onChange, onComplete],
  );

  const handleChange = (index: number) => (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value.replace(/\D/g, '');
    if (!raw) {
      setDigit(index, '');
      return;
    }
    // 多字符粘贴或快速输入：只取最后一位到当前格，其余顺延
    const chars = raw.split('');
    if (chars.length === 1) {
      setDigit(index, chars[0]);
      return;
    }
    const next = digits.slice();
    for (let i = 0; i < chars.length && index + i < length; i += 1) {
      next[index + i] = chars[i];
    }
    onChange?.(next.join(''));
    const lastFilled = Math.min(index + chars.length, length) - 1;
    refs.current[lastFilled]?.focus();
    if (next.every((d) => d !== '') && onComplete) {
      onComplete();
    }
  };

  const handleKeyDown = (index: number) => (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace') {
      if (digits[index]) {
        setDigit(index, '');
      } else if (index > 0) {
        refs.current[index - 1]?.focus();
        setDigit(index - 1, '');
      }
      e.preventDefault();
    } else if (e.key === 'ArrowLeft' && index > 0) {
      refs.current[index - 1]?.focus();
    } else if (e.key === 'ArrowRight' && index < length - 1) {
      refs.current[index + 1]?.focus();
    }
  };

  const handlePaste = (index: number) => (e: React.ClipboardEvent<HTMLInputElement>) => {
    const text = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length);
    if (!text) return;
    e.preventDefault();
    const next = digits.slice();
    for (let i = 0; i < text.length && index + i < length; i += 1) {
      next[index + i] = text[i];
    }
    onChange?.(next.join(''));
    const lastFilled = Math.min(index + text.length, length) - 1;
    refs.current[lastFilled]?.focus();
    if (next.every((d) => d !== '') && onComplete) {
      onComplete();
    }
  };

  return (
    <div className={`otp-input${disabled ? ' is-disabled' : ''}`} role="group" aria-label="平安令牌">
      {Array.from({ length }).map((_, index) => (
        <input
          key={index}
          ref={(el) => {
            refs.current[index] = el;
          }}
          className={`otp-cell${digits[index] ? ' is-filled' : ''}`}
          inputMode="numeric"
          pattern="[0-9]*"
          maxLength={1}
          autoComplete="one-time-code"
          value={digits[index] ?? ''}
          disabled={disabled}
          autoFocus={autoFocus && index === 0}
          onChange={handleChange(index)}
          onKeyDown={handleKeyDown(index)}
          onPaste={handlePaste(index)}
          aria-label={`第 ${index + 1} 位`}
        />
      ))}
    </div>
  );
};

function useMemoDigits(value: string, length: number): string[] {
  return React.useMemo(() => {
    // 始终返回长度为 length 的数组：未填位置用空串占位
    // 注意：padEnd(x, '') 在填充串为空时不会补齐，必须用 Array.from 显式构造
    return Array.from({ length }, (_, i) => value[i] ?? '');
  }, [value, length]);
}

export default OtpInput;
