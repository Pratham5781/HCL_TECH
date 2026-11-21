import { ShieldCheck, UserRound } from 'lucide-react';
import { useAuth } from '../store/AuthContext';

const descriptions = {
  admin: 'You are signed in to the administration portal. Your account can access administration features as they are enabled.',
  operator: 'You are signed in to the field operations portal. Your account can access assigned operational work as it is enabled.',
  support: 'You are signed in to the support portal. Your account can access customer support work as it is enabled.',
  customer: 'You are signed in to the customer portal. Your account can access utility services as they are enabled.'
};

export default function PortalHome({ portal }) {
  const { user } = useAuth();
  return <div className="mx-auto max-w-4xl">
    <p className="text-sm text-slate-500">Home / Overview</p>
    <h1 className="mt-2 text-2xl font-bold text-slate-900">Portal overview</h1>
    <p className="mt-1 text-slate-500">UtilityCare account access</p>
    <section className="mt-6 grid gap-5 md:grid-cols-2">
      <article className="card"><div className="flex items-center gap-3"><span className="rounded-lg bg-emerald-50 p-2 text-emerald-700"><ShieldCheck size={20}/></span><h2 className="font-semibold">Secure session</h2></div><p className="mt-4 text-sm leading-6 text-slate-600">{descriptions[portal]}</p></article>
      <article className="card"><div className="flex items-center gap-3"><span className="rounded-lg bg-sky-50 p-2 text-sky-700"><UserRound size={20}/></span><h2 className="font-semibold">Account details</h2></div><dl className="mt-4 space-y-3 text-sm"><div className="flex justify-between gap-3"><dt className="text-slate-500">Email</dt><dd className="font-medium text-slate-800">{user?.email}</dd></div><div className="flex justify-between gap-3"><dt className="text-slate-500">Portal role</dt><dd className="font-medium text-slate-800">{user?.role}</dd></div><div className="flex justify-between gap-3"><dt className="text-slate-500">Session</dt><dd className="font-medium text-emerald-700">Authenticated</dd></div></dl></article>
    </section>
  </div>;
}
