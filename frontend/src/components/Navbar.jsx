import { useAuth } from '../context/AuthContext.jsx';
import { Logo } from '../pages/AuthPage.jsx';

export default function Navbar() {
  const { user, logout } = useAuth();
  const initials = (user?.name || '?')
    .split(' ')
    .map((p) => p[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();

  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <div className="flex items-center gap-2 font-semibold text-brand-700">
          <Logo /> RiskLens
        </div>
        <div className="flex items-center gap-3">
          <div className="hidden text-right sm:block">
            <div className="text-sm font-medium text-slate-900">{user?.name}</div>
            <div className="text-xs text-slate-500">{user?.email}</div>
          </div>
          <div className="flex h-9 w-9 items-center justify-center rounded-full bg-brand-100 text-sm font-semibold text-brand-700">
            {initials}
          </div>
          <button type="button" onClick={logout} className="btn-ghost">
            Sign out
          </button>
        </div>
      </div>
    </header>
  );
}
