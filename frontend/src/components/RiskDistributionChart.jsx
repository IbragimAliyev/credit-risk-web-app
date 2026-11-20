import { BarElement, CategoryScale, Chart as ChartJS, LinearScale, Tooltip } from 'chart.js';
import { Bar } from 'react-chartjs-2';
import { RISK_LABELS, RISK_STYLES } from '../utils/format.js';

ChartJS.register(CategoryScale, LinearScale, BarElement, Tooltip);

const INK_MUTED = '#64748b'; // slate-500
const GRID = '#e2e8f0'; // slate-200
const BASELINE = '#cbd5e1'; // slate-300

/**
 * Single-series bar chart of LOW / MEDIUM / HIGH counts.
 * Bars use the status palette; category names on the x-axis carry identity,
 * so the meaning never depends on color alone. No legend (one series).
 */
export default function RiskDistributionChart({ counts }) {
  const total = RISK_LABELS.reduce((sum, l) => sum + counts[l], 0);

  const data = {
    labels: RISK_LABELS,
    datasets: [
      {
        label: 'Applications',
        data: RISK_LABELS.map((l) => counts[l]),
        backgroundColor: RISK_LABELS.map((l) => RISK_STYLES[l].hex),
        hoverBackgroundColor: RISK_LABELS.map((l) => RISK_STYLES[l].hex),
        borderRadius: { topLeft: 4, topRight: 4 },
        borderSkipped: 'bottom',
        maxBarThickness: 56,
        categoryPercentage: 0.7,
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    animation: { duration: 300 },
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: '#0f172a',
        padding: 10,
        displayColors: false,
        callbacks: {
          label: (ctx) => {
            const pct = total ? Math.round((ctx.parsed.y / total) * 100) : 0;
            return `${ctx.parsed.y} application${ctx.parsed.y === 1 ? '' : 's'} (${pct}%)`;
          },
        },
      },
    },
    scales: {
      x: {
        grid: { display: false },
        border: { color: BASELINE },
        ticks: { color: INK_MUTED, font: { weight: 600 } },
      },
      y: {
        beginAtZero: true,
        grid: { color: GRID },
        border: { display: false },
        ticks: { color: INK_MUTED, precision: 0 },
      },
    },
  };

  return (
    <div>
      <div className="relative h-64">
        <Bar data={data} options={options} aria-label="Bar chart of applications by risk label" role="img" />
        {total === 0 && (
          <div className="absolute inset-0 flex items-center justify-center text-sm text-slate-400">
            No data yet
          </div>
        )}
      </div>

      {/* Table view of the same data (accessible alternative to the chart). */}
      <table className="mt-4 w-full text-sm">
        <caption className="sr-only">Applications by risk label</caption>
        <tbody>
          {RISK_LABELS.map((l) => (
            <tr key={l} className="border-t border-slate-100">
              <th scope="row" className="py-1.5 text-left font-medium text-slate-700">
                <span
                  className="mr-2 inline-block h-2.5 w-2.5 rounded-sm align-middle"
                  style={{ backgroundColor: RISK_STYLES[l].hex }}
                  aria-hidden="true"
                />
                {l}
              </th>
              <td className="py-1.5 text-right tabular-nums text-slate-900">{counts[l]}</td>
              <td className="w-16 py-1.5 text-right tabular-nums text-slate-500">
                {total ? `${Math.round((counts[l] / total) * 100)}%` : '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
