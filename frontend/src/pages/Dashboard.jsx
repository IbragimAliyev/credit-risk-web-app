import { useCallback, useEffect, useMemo, useState } from 'react';
import { applicationsApi, modelApi, parseApiError } from '../api/client.js';
import ApplicationForm from '../components/ApplicationForm.jsx';
import ApplicationsTable from '../components/ApplicationsTable.jsx';
import Navbar from '../components/Navbar.jsx';
import ResultCard from '../components/ResultCard.jsx';
import RiskDistributionChart from '../components/RiskDistributionChart.jsx';
import { formatPercent, RISK_LABELS, RISK_STYLES } from '../utils/format.js';

export default function Dashboard() {
  const [applications, setApplications] = useState([]);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [modelInfo, setModelInfo] = useState(null);

  const loadApplications = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      setApplications(await applicationsApi.list());
    } catch (err) {
      setLoadError(parseApiError(err).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadApplications();
  }, [loadApplications]);

  useEffect(() => {
    // Model details are informational; the dashboard works without them.
    modelApi.info().then(setModelInfo).catch(() => setModelInfo(null));
  }, []);

  const handleCreated = (created) => {
    setApplications((prev) => [created, ...prev]);
    setSelected(created);
  };

  const handleSelect = async (id) => {
    try {
      setSelected(await applicationsApi.get(id));
    } catch (err) {
      setLoadError(parseApiError(err).message);
    }
  };

  const counts = useMemo(() => {
    const c = { LOW: 0, MEDIUM: 0, HIGH: 0 };
    applications.forEach((a) => {
      c[a.riskLabel] += 1;
    });
    return c;
  }, [applications]);

  const avgProbability = applications.length
    ? applications.reduce((sum, a) => sum + Number(a.defaultProbability), 0) / applications.length
    : null;

  return (
    <div className="min-h-screen">
      <Navbar />

      <main className="mx-auto max-w-7xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
        <div>
          <h1 className="text-2xl font-bold">Dashboard</h1>
          <p className="text-sm text-slate-500">Submit applications and review risk assessments.</p>
        </div>

        {/* KPI row */}
        <div className="grid grid-cols-2 gap-4 md:grid-cols-5">
          <Stat label="Total applications" value={applications.length} />
          <Stat label="Avg. default probability" value={formatPercent(avgProbability)} />
          {RISK_LABELS.map((label) => (
            <Stat
              key={label}
              label={`${label.charAt(0)}${label.slice(1).toLowerCase()} risk`}
              value={counts[label]}
              dotClass={RISK_STYLES[label].bar}
            />
          ))}
        </div>

        {modelInfo && <ModelStrip info={modelInfo} />}

        {loadError && (
          <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {loadError}
          </div>
        )}

        <div className="grid items-start gap-6 lg:grid-cols-5">
          <section className="card p-6 lg:col-span-3">
            <h2 className="text-lg font-semibold">New loan application</h2>
            <p className="mb-5 text-sm text-slate-500">All amounts in USD. Income is annual.</p>
            <ApplicationForm onCreated={handleCreated} />
          </section>

          <section className="lg:col-span-2">
            <ResultCard application={selected} modelInfo={modelInfo} />
          </section>
        </div>

        <div className="grid gap-6 lg:grid-cols-5">
          <section className="card overflow-hidden lg:col-span-3">
            <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
              <h2 className="text-lg font-semibold">Past applications</h2>
              <button type="button" className="btn-ghost" onClick={loadApplications} disabled={loading}>
                {loading ? 'Loading…' : 'Refresh'}
              </button>
            </div>
            <ApplicationsTable
              applications={applications}
              loading={loading}
              selectedId={selected?.id}
              onSelect={handleSelect}
            />
          </section>

          <section className="card p-6 lg:col-span-2">
            <h2 className="text-lg font-semibold">Risk distribution</h2>
            <p className="mb-4 text-sm text-slate-500">Number of applications per risk label</p>
            <RiskDistributionChart counts={counts} />
          </section>
        </div>
      </main>
    </div>
  );
}

function Stat({ label, value, dotClass }) {
  return (
    <div className="card px-4 py-3">
      <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-slate-500">
        {dotClass && <span className={`h-2 w-2 rounded-full ${dotClass}`} aria-hidden="true" />}
        {label}
      </div>
      <div className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">{value}</div>
    </div>
  );
}

/** One-line summary of the model that scores applications, with its held-out test performance. */
function ModelStrip({ info }) {
  const m = info.metrics;
  return (
    <div className="flex flex-wrap items-center gap-x-6 gap-y-1 rounded-lg border border-brand-100 bg-brand-50 px-4 py-2.5 text-sm text-slate-700">
      <span className="font-semibold text-brand-700">Scored by a logistic regression model</span>
      <span>
        Trained on <b className="tabular-nums">{m.trainingRows.toLocaleString()}</b> real loans
      </span>
      <span title="Area under the ROC curve on held-out test data (0.5 = random, 1.0 = perfect)">
        Test AUC <b className="tabular-nums">{m.testAUC.toFixed(3)}</b>
      </span>
      <span>
        Gini <b className="tabular-nums">{m.gini.toFixed(2)}</b>
      </span>
      <span className="text-slate-500">
        Bands: Low &lt; {formatPercent(info.thresholds.low)} ≤ Medium &lt; {formatPercent(info.thresholds.high)} ≤ High
      </span>
      <span className="ml-auto text-xs text-slate-400">{info.modelVersion}</span>
    </div>
  );
}
