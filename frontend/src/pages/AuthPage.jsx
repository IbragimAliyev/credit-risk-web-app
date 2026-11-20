import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { parseApiError } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';

export default function AuthPage() {
  const { login, register } = useAuth();
  const navigate = useNavigate();

  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const isRegister = mode === 'register';

  const switchMode = (next) => {
    setMode(next);
    setError(null);
    setFieldErrors({});
  };

  const update = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});
    try {
      if (isRegister) {
        await register(form.name, form.email, form.password);
      } else {
        await login(form.email, form.password);
      }
      navigate('/', { replace: true });
    } catch (err) {
      const { message, fieldErrors: fe } = parseApiError(err);
      setError(message);
      setFieldErrors(fe);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen">
      {/* Brand panel */}
      <div className="hidden w-1/2 flex-col justify-between bg-gradient-to-br from-brand-900 via-brand-700 to-brand-500 p-12 text-white lg:flex">
        <div className="flex items-center gap-2 text-lg font-semibold">
          <Logo /> RiskLens
        </div>
        <div>
          <h1 className="text-4xl font-bold leading-tight">Credit risk decisions, in seconds.</h1>
          <p className="mt-4 max-w-md text-brand-100">
            Score loan applications with a transparent, rule-based model and track your portfolio's
            risk distribution at a glance.
          </p>
        </div>
        <p className="text-sm text-brand-100">© {new Date().getFullYear()} RiskLens</p>
      </div>

      {/* Form panel */}
      <div className="flex w-full items-center justify-center p-6 lg:w-1/2">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-2 text-lg font-semibold text-brand-700 lg:hidden">
            <Logo /> RiskLens
          </div>

          <h2 className="text-2xl font-bold">{isRegister ? 'Create an account' : 'Welcome back'}</h2>
          <p className="mt-1 text-sm text-slate-500">
            {isRegister ? 'Start assessing applications in minutes.' : 'Sign in to your dashboard.'}
          </p>

          <div className="mt-6 grid grid-cols-2 rounded-lg bg-slate-100 p-1 text-sm font-medium">
            {['login', 'register'].map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => switchMode(m)}
                className={`rounded-md py-2 transition ${
                  mode === m ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                {m === 'login' ? 'Sign in' : 'Register'}
              </button>
            ))}
          </div>

          <form onSubmit={handleSubmit} className="mt-6 space-y-4" noValidate>
            {isRegister && (
              <Field label="Full name" error={fieldErrors.name}>
                <input
                  name="name"
                  value={form.name}
                  onChange={update}
                  className={`input ${fieldErrors.name ? 'input-error' : ''}`}
                  placeholder="Jane Doe"
                  autoComplete="name"
                  required
                />
              </Field>
            )}
            <Field label="Email" error={fieldErrors.email}>
              <input
                name="email"
                type="email"
                value={form.email}
                onChange={update}
                className={`input ${fieldErrors.email ? 'input-error' : ''}`}
                placeholder="you@company.com"
                autoComplete="email"
                required
              />
            </Field>
            <Field label="Password" error={fieldErrors.password}>
              <input
                name="password"
                type="password"
                value={form.password}
                onChange={update}
                className={`input ${fieldErrors.password ? 'input-error' : ''}`}
                placeholder={isRegister ? 'At least 8 characters' : '••••••••'}
                autoComplete={isRegister ? 'new-password' : 'current-password'}
                required
              />
            </Field>

            {error && (
              <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {error}
              </div>
            )}

            <button type="submit" className="btn-primary w-full" disabled={submitting}>
              {submitting ? 'Please wait…' : isRegister ? 'Create account' : 'Sign in'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

function Field({ label, error, children }) {
  return (
    <div>
      <label className="label">{label}</label>
      {children}
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  );
}

export function Logo() {
  return (
    <svg viewBox="0 0 24 24" className="h-7 w-7" fill="none" aria-hidden="true">
      <rect width="24" height="24" rx="6" fill="currentColor" opacity="0.15" />
      <path d="M6 16l4-4 3 3 5-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
