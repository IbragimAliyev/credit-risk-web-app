import { RISK_STYLES } from '../utils/format.js';

/** Color-coded label; always shows icon + text so meaning never relies on color alone. */
export default function RiskBadge({ label, size = 'sm' }) {
  const style = RISK_STYLES[label];
  if (!style) return null;
  const sizing = size === 'lg' ? 'px-3 py-1 text-sm' : 'px-2 py-0.5 text-xs';
  return (
    <span className={`inline-flex items-center gap-1 rounded-full font-semibold ring-1 ring-inset ${style.badge} ${sizing}`}>
      <span aria-hidden="true">{style.icon}</span>
      {label}
    </span>
  );
}
