import { formatCurrency, formatDate, formatPercent, loanIntentLabel, RISK_STYLES } from '../utils/format.js';
import RiskBadge from './RiskBadge.jsx';

const DEFAULT_THRESHOLDS = { low: 0.15, high: 0.4 };

export default function ResultCard({ application, modelInfo }) {
  if (!application) {
    return (
      <div className="card flex h-full min-h-[320px] flex-col items-center justify-center p-6 text-center">
        <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-xl text-slate-400">
          ?
        </div>
        <h2 className="text-lg font-semibold">No assessment selected</h2>
        <p className="mt-1 max-w-xs text-sm text-slate-500">
          Submit an application or pick one from the table to see its risk score and breakdown.
        </p>
      </div>
    );
  }

  const style = RISK_STYLES[application.riskLabel];
  const thresholds = modelInfo?.thresholds ?? DEFAULT_THRESHOLDS;

  return (
    <div className="card h-full p-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Assessment #{application.id}</p>
          <h2 className="text-lg font-semibold">{application.applicantName}</h2>
          <p className="text-xs text-slate-500">{formatDate(application.createdAt)}</p>
        </div>
        <RiskBadge label={application.riskLabel} size="lg" />
      </div>

      <div className={`mt-5 rounded-lg border p-4 ${style.panel}`}>
        <div className="flex items-baseline gap-2">
          <span className={`text-5xl font-bold tabular-nums ${style.text}`}>
            {formatPercent(application.defaultProbability)}
          </span>
          <span className="text-sm text-slate-600">estimated probability of default</span>
        </div>
        <ScoreScale probability={Number(application.defaultProbability)} thresholds={thresholds} />
        <p className="mt-3 text-sm text-slate-700">{style.description}</p>
      </div>

      <div className="mt-6 flex items-baseline justify-between">
        <h3 className="text-sm font-semibold text-slate-700">What drove this score</h3>
        <span className="text-xs text-slate-500">vs. an average applicant</span>
      </div>
      <Breakdown items={application.breakdown} />

      <dl className="mt-6 grid grid-cols-2 gap-x-4 gap-y-2 border-t border-slate-200 pt-4 text-sm">
        <Detail term="Annual income" value={formatCurrency(application.income)} />
        <Detail term="Loan amount" value={formatCurrency(application.loanAmount)} />
        <Detail term="Purpose" value={loanIntentLabel(application.loanIntent)} />
        <Detail term="Age" value={application.age} />
      </dl>
      <p className="mt-4 text-[11px] text-slate-400">Model {application.modelVersion}</p>
    </div>
  );
}

/**
 * Diverging bars centred on "average applicant": red to the right raises risk,
 * green to the left lowers it. Each row also says it in words, so color is never the only cue.
 */
function Breakdown({ items }) {
  const maxAbs = Math.max(0.5, ...items.map((i) => Math.abs(i.contribution)));

  return (
    <ul className="mt-3 space-y-2.5">
      {items.map((item) => {
        const raises = item.effect === 'INCREASES_RISK';
        const width = `${(Math.abs(item.contribution) / maxAbs) * 50}%`;
        const negligible = Math.abs(item.contribution) < 0.02;
        return (
          <li key={item.factor}>
            <div className="flex items-baseline justify-between gap-2 text-sm">
              <span className="font-medium text-slate-800">{item.factor}</span>
              <span className={`shrink-0 text-xs font-medium ${negligible ? 'text-slate-400' : raises ? 'text-red-700' : 'text-green-700'}`}>
                {negligible ? 'little effect' : raises ? '↑ raises risk' : '↓ lowers risk'}
              </span>
            </div>
            <div className="relative mt-1 h-2 rounded-full bg-slate-100" aria-hidden="true">
              <div className="absolute inset-y-[-2px] left-1/2 w-px bg-slate-300" />
              <div
                className={`absolute inset-y-0 ${raises ? 'left-1/2 rounded-r-full bg-red-500' : 'right-1/2 rounded-l-full bg-green-600'}`}
                style={{ width }}
              />
            </div>
            <p className="mt-0.5 text-xs text-slate-500">{item.detail}</p>
          </li>
        );
      })}
    </ul>
  );
}

/** 0–100% scale split at the model's LOW/MEDIUM/HIGH thresholds, with a marker at this applicant. */
function ScoreScale({ probability, thresholds }) {
  const low = thresholds.low * 100;
  const high = thresholds.high * 100;
  return (
    <div className="mt-3" aria-hidden="true">
      <div className="relative flex h-2 gap-0.5">
        <div className="h-full rounded-l-full bg-green-600/70" style={{ width: `${low}%` }} />
        <div className="h-full bg-amber-500/70" style={{ width: `${high - low}%` }} />
        <div className="h-full flex-1 rounded-r-full bg-red-600/70" />
        <div
          className="absolute -top-1 h-4 w-1 -translate-x-1/2 rounded-full bg-slate-900 ring-2 ring-white"
          style={{ left: `${Math.min(100, Math.max(0, probability * 100))}%` }}
        />
      </div>
      <div className="relative mt-1 h-3 text-[10px] font-medium uppercase text-slate-500">
        <span className="absolute left-0">Low</span>
        <span className="absolute -translate-x-1/2" style={{ left: `${(low + high) / 2}%` }}>
          Medium
        </span>
        <span className="absolute -translate-x-1/2" style={{ left: `${(high + 100) / 2}%` }}>
          High
        </span>
      </div>
    </div>
  );
}

function Detail({ term, value }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{term}</dt>
      <dd className="font-medium text-slate-800">{value}</dd>
    </div>
  );
}
