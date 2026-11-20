const currency = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 0,
});

const dateTime = new Intl.DateTimeFormat('en-US', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

export const formatCurrency = (value) => currency.format(Number(value));
const dateOnly = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' });

export const formatDate = (iso) => (iso ? dateTime.format(new Date(iso)) : '—');
export const formatShortDate = (iso) => (iso ? dateOnly.format(new Date(iso)) : '—');

/** Status colors per risk label (green / amber / red), used across badges, cards and the chart. */
export const RISK_STYLES = {
  LOW: {
    hex: '#0ca30c',
    badge: 'bg-green-50 text-green-800 ring-green-600/30',
    panel: 'border-green-200 bg-green-50',
    text: 'text-green-700',
    bar: 'bg-green-600',
    icon: '✓',
    description: 'Low estimated chance of default. Standard terms recommended.',
  },
  MEDIUM: {
    hex: '#fab219',
    badge: 'bg-amber-50 text-amber-800 ring-amber-600/30',
    panel: 'border-amber-200 bg-amber-50',
    text: 'text-amber-700',
    bar: 'bg-amber-500',
    icon: '!',
    description: 'Moderate risk. Consider additional review or adjusted terms.',
  },
  HIGH: {
    hex: '#d03b3b',
    badge: 'bg-red-50 text-red-800 ring-red-600/30',
    panel: 'border-red-200 bg-red-50',
    text: 'text-red-700',
    bar: 'bg-red-600',
    icon: '✕',
    description: 'Elevated default risk. Manual underwriting strongly advised.',
  },
};

export const RISK_LABELS = ['LOW', 'MEDIUM', 'HIGH'];

/** Formats a 0–1 probability. Extremes show as "<1%" / ">99%": a model is never certain. */
export const formatPercent = (value) => {
  if (value == null) return '—';
  const pct = Number(value) * 100;
  if (pct > 0 && pct < 0.5) return '<1%';
  if (pct >= 99.5 && pct < 100) return '>99%';
  return `${Math.round(pct)}%`;
};

export const HOME_OWNERSHIP_OPTIONS = [
  { value: 'RENT', label: 'Rent' },
  { value: 'MORTGAGE', label: 'Mortgage' },
  { value: 'OWN', label: 'Own outright' },
  { value: 'OTHER', label: 'Other' },
];

export const LOAN_INTENT_OPTIONS = [
  { value: 'DEBTCONSOLIDATION', label: 'Debt consolidation' },
  { value: 'EDUCATION', label: 'Education' },
  { value: 'HOMEIMPROVEMENT', label: 'Home improvement' },
  { value: 'MEDICAL', label: 'Medical' },
  { value: 'PERSONAL', label: 'Personal' },
  { value: 'VENTURE', label: 'Business venture' },
];

export const loanIntentLabel = (value) => LOAN_INTENT_OPTIONS.find((o) => o.value === value)?.label ?? value;
