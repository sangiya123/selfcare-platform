/**
 * Formatters — Utility functions for formatting values in generic widgets.
 * All formatting is locale-aware and theme-driven.
 */

export function formatValue(
  value: unknown,
  format?: 'currency' | 'number' | 'date' | 'datetime' | 'percentage' | 'text' | 'bytes' | 'relative-time',
  options?: { currency?: string; locale?: string }
): string {
  if (value === undefined || value === null) return '—';

  const locale = options?.locale || 'en-LK';
  const currency = options?.currency || 'LKR';

  switch (format) {
    case 'relative-time':
      return formatRelativeTime(value instanceof Date ? value : new Date(String(value)), locale);
    case 'currency': {
      const num = Number(value);
      if (isNaN(num)) return String(value);
      return new Intl.NumberFormat(locale, {
        style: 'currency',
        currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(num);
    }
    case 'number': {
      const num = Number(value);
      if (isNaN(num)) return String(value);
      return new Intl.NumberFormat(locale).format(num);
    }
    case 'percentage': {
      const num = Number(value);
      if (isNaN(num)) return String(value);
      return new Intl.NumberFormat(locale, {
        style: 'percent',
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
      }).format(num / 100);
    }
    case 'date': {
      const date = value instanceof Date ? value : new Date(String(value));
      if (isNaN(date.getTime())) return String(value);
      return new Intl.DateTimeFormat(locale, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      }).format(date);
    }
    case 'datetime': {
      const date = value instanceof Date ? value : new Date(String(value));
      if (isNaN(date.getTime())) return String(value);
      return new Intl.DateTimeFormat(locale, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      }).format(date);
    }
    case 'bytes': {
      const bytes = Number(value);
      if (isNaN(bytes)) return String(value);
      const units = ['B', 'KB', 'MB', 'GB', 'TB'];
      let size = bytes;
      let unitIndex = 0;
      while (size >= 1024 && unitIndex < units.length - 1) {
        size /= 1024;
        unitIndex++;
      }
      return `${size.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
    }
    case 'text':
    default:
      return String(value);
  }
}

export function formatRelativeTime(date: Date | string, locale = 'en-LK'): string {
  const d = date instanceof Date ? date : new Date(String(date));
  if (isNaN(d.getTime())) return String(date);

  const now = new Date();
  const diffMs = now.getTime() - d.getTime();
  const diffSecs = Math.floor(diffMs / 1000);
  const diffMins = Math.floor(diffSecs / 60);
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);

  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });

  if (diffSecs < 60) return rtf.format(-diffSecs, 'second');
  if (diffMins < 60) return rtf.format(-diffMins, 'minute');
  if (diffHours < 24) return rtf.format(-diffHours, 'hour');
  if (diffDays < 30) return rtf.format(-diffDays, 'day');
  
  return new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'short', day: 'numeric' }).format(d);
}

export function truncate(text: string, maxLength: number, suffix = '…'): string {
  if (text.length <= maxLength) return text;
  return text.slice(0, maxLength - suffix.length) + suffix;
}