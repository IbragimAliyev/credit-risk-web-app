import { useState } from 'react';
import { applicationsApi, parseApiError } from '../api/client.js';
import { HOME_OWNERSHIP_OPTIONS, LOAN_INTENT_OPTIONS } from '../utils/format.js';

const EMPTY = {
  applicantName: '',
  age: '',
  income: '',
  loanAmount: '',
  employmentYears: '',
  creditHistoryYears: '',
  homeOwnership: '',
  loanIntent: '',
  previousDefault: '',
};

const FIELDS = [
  { name: 'applicantName', label: 'Applicant name', type: 'text', placeholder: 'Jane Doe', span: 2 },
  { name: 'age', label: 'Age', type: 'number', placeholder: '35', min: 18, max: 100 },
  { name: 'income', label: 'Annual income ($)', type: 'number', placeholder: '65000', min: 1, step: '0.01' },
  { name: 'loanAmount', label: 'Loan amount ($)', type: 'number', placeholder: '20000', min: 1, step: '0.01' },
  { name: 'loanIntent', label: 'Loan purpose', options: LOAN_INTENT_OPTIONS },
  { name: 'employmentYears', label: 'Years employed', type: 'number', placeholder: '5', min: 0, max: 60 },
  { name: 'creditHistoryYears', label: 'Credit history (years)', type: 'number', placeholder: '6', min: 0, max: 60 },
  { name: 'homeOwnership', label: 'Home ownership', options: HOME_OWNERSHIP_OPTIONS },
  {
    name: 'previousDefault',
    label: 'Previous default on file?',
    options: [
      { value: 'false', label: 'No' },
      { value: 'true', label: 'Yes' },
    ],
  },
];

export default function ApplicationForm({ onCreated }) {
  const [form, setForm] = useState(EMPTY);
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});

    const payload = {
      applicantName: form.applicantName.trim(),
      age: toNumber(form.age),
      income: toNumber(form.income),
      loanAmount: toNumber(form.loanAmount),
      employmentYears: toNumber(form.employmentYears),
      creditHistoryYears: toNumber(form.creditHistoryYears),
      homeOwnership: form.homeOwnership || null,
      loanIntent: form.loanIntent || null,
      previousDefault: form.previousDefault === '' ? null : form.previousDefault === 'true',
    };

    try {
      const created = await applicationsApi.create(payload);
      onCreated(created);
      setForm(EMPTY);
    } catch (err) {
      const { message, fieldErrors: fe } = parseApiError(err);
      setError(message);
      setFieldErrors(fe);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {FIELDS.map(({ name, label, span, options, ...inputProps }) => {
          const className = `input ${fieldErrors[name] ? 'input-error' : ''}`;
          return (
            <div key={name} className={span === 2 ? 'sm:col-span-2' : ''}>
              <label htmlFor={name} className="label">
                {label}
              </label>
              {options ? (
                <select id={name} name={name} value={form[name]} onChange={update} className={className} required>
                  <option value="" disabled>
                    Select…
                  </option>
                  {options.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  id={name}
                  name={name}
                  value={form[name]}
                  onChange={update}
                  className={className}
                  required
                  {...inputProps}
                />
              )}
              {fieldErrors[name] && <p className="mt-1 text-xs text-red-600">{fieldErrors[name]}</p>}
            </div>
          );
        })}
      </div>

      {error && (
        <div role="alert" className="mt-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="mt-6 flex justify-end gap-2">
        <button type="button" className="btn-ghost" onClick={() => setForm(EMPTY)} disabled={submitting}>
          Clear
        </button>
        <button type="submit" className="btn-primary" disabled={submitting}>
          {submitting ? 'Scoring…' : 'Assess risk'}
        </button>
      </div>
    </form>
  );
}

function toNumber(value) {
  return value === '' ? null : Number(value);
}
