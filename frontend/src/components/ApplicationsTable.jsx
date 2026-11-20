import { formatCurrency, formatDate, formatPercent, formatShortDate, loanIntentLabel } from '../utils/format.js';
import RiskBadge from './RiskBadge.jsx';

export default function ApplicationsTable({ applications, loading, selectedId, onSelect }) {
  if (loading && applications.length === 0) {
    return <p className="px-6 py-10 text-center text-sm text-slate-500">Loading applications…</p>;
  }
  if (applications.length === 0) {
    return (
      <p className="px-6 py-10 text-center text-sm text-slate-500">
        No applications yet. Submit your first one above.
      </p>
    );
  }

  return (
    <div className="max-h-[420px] overflow-auto">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="sticky top-0 bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-6 py-3">Applicant</th>
            <th className="px-3 py-3 text-right">Loan</th>
            <th className="px-3 py-3">Purpose</th>
            <th className="px-3 py-3 text-right" title="Estimated probability of default">
              Default prob.
            </th>
            <th className="px-3 py-3">Risk</th>
            <th className="px-6 py-3">Date</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 bg-white">
          {applications.map((app) => (
            <tr
              key={app.id}
              onClick={() => onSelect(app.id)}
              onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && onSelect(app.id)}
              tabIndex={0}
              className={`cursor-pointer transition hover:bg-slate-50 focus:bg-slate-50 focus:outline-none ${
                selectedId === app.id ? 'bg-brand-50 hover:bg-brand-50' : ''
              }`}
            >
              <td className="whitespace-nowrap px-6 py-3 font-medium text-slate-900">{app.applicantName}</td>
              <td className="whitespace-nowrap px-3 py-3 text-right tabular-nums text-slate-600">
                {formatCurrency(app.loanAmount)}
              </td>
              <td className="whitespace-nowrap px-3 py-3 text-slate-600">{loanIntentLabel(app.loanIntent)}</td>
              <td className="px-3 py-3 text-right font-semibold tabular-nums text-slate-900">
                {formatPercent(app.defaultProbability)}
              </td>
              <td className="px-3 py-3">
                <RiskBadge label={app.riskLabel} />
              </td>
              <td className="whitespace-nowrap px-6 py-3 text-slate-500" title={formatDate(app.createdAt)}>
                {formatShortDate(app.createdAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
