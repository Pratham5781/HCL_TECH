import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { Navigate, useLocation, useNavigate, Link } from 'react-router-dom';
import { ArrowRight, Headset, ShieldCheck, UserRound, Wrench, Zap, Droplets, Flame, Eye, EyeOff } from 'lucide-react';
import toast from 'react-hot-toast';
import { useAuth } from '../../store/AuthContext';
import ThemeToggle from '../../components/common/ThemeToggle';

const portals = {
 admin: { role: 'ADMIN', label: 'Administration', color: 'indigo', subtitle: 'Manage utility operations and service delivery.' },
 operator: { role: 'OPERATOR', label: 'Field Operations', color: 'emerald', subtitle: 'Manage assigned zones and customer readings.' },
 support: { role: 'HELP_SUPPORT', label: 'Customer Support', color: 'amber', subtitle: 'Help customers resolve service requests.' },
 customer: { role: 'CUSTOMER', label: 'Customer Portal', color: 'sky', subtitle: 'View bills, payments and your utility services.' }
};
const schema = yup.object({ email: yup.string().email('Enter a valid email').required('Email is required'), password: yup.string().required('Password is required') });
const icons = [Zap, Droplets, Flame];
const portalChoices = [
 { path:'/admin/login', title:'Admin', description:'Manage users, approvals, services, and reports.', role:'Administration', icon:ShieldCheck, color:'bg-indigo-600', text:'group-hover:text-indigo-700' },
 { path:'/support/login', title:'Support', description:'Review customer requests and resolve service issues.', role:'Customer support', icon:Headset, color:'bg-amber-500', text:'group-hover:text-amber-700' },
 { path:'/operator/login', title:'Operator', description:'Handle assigned zones, readings, and utility operations.', role:'Field operations', icon:Wrench, color:'bg-emerald-600', text:'group-hover:text-emerald-700' },
 { path:'/customer/login', title:'Customer', description:'View bills, payments, usage, and service requests.', role:'Customer portal', icon:UserRound, color:'bg-sky-600', text:'group-hover:text-sky-700' },
];
export default function LoginPage({ portal }) {
 const config = portals[portal]; const { user, login } = useAuth(); const navigate = useNavigate(); const location = useLocation();
 const [showPassword, setShowPassword] = useState(false); const [busy, setBusy] = useState(false);
 const { register, handleSubmit, formState: { errors } } = useForm({ resolver: yupResolver(schema) });
 if (portal === 'chooser') return <main className="relative min-h-screen bg-slate-50 px-5 py-14"><div className="absolute right-5 top-5 z-10"><ThemeToggle/></div><section className="mx-auto max-w-5xl"><div className="text-center"><div className="mx-auto mb-5 inline-flex rounded-2xl bg-sky-600 p-3 text-white shadow-sm"><Zap size={26}/></div><p className="text-sm font-semibold uppercase tracking-[0.18em] text-sky-700">UtilityCare</p><h1 className="mt-3 text-3xl font-bold text-slate-900 sm:text-4xl">Choose your portal</h1><p className="mx-auto mt-3 max-w-xl text-slate-600">Select the role you use to access UtilityCare. Each portal has its own sign-in page.</p></div><div className="mt-10 grid gap-4 sm:grid-cols-2">{portalChoices.map(({path,title,description,role,icon:Icon,color,text})=><Link key={path} to={path} className="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-md focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2"><div className="flex items-start justify-between gap-4"><span className={`rounded-xl p-3 text-white ${color}`}><Icon size={22}/></span><ArrowRight size={19} className="mt-1 text-slate-400 transition group-hover:translate-x-1"/></div><p className="mt-5 text-xs font-semibold uppercase tracking-wider text-slate-500">{role}</p><h2 className={`mt-1 text-xl font-bold text-slate-900 ${text}`}>{title}</h2><p className="mt-2 text-sm leading-6 text-slate-600">{description}</p><span className={`mt-5 inline-block text-sm font-semibold ${text}`}>Go to {title} sign in</span></Link>)}</div><p className="mt-8 text-center text-sm text-slate-500">New customer? <Link to="/customer/register" className="font-semibold text-sky-700 hover:underline">Create an account</Link></p><p className="mt-3 text-center text-xs text-slate-400">Secure access · Your account is protected</p></section></main>;
 if (user?.role === config.role) return <Navigate to={`/${portal}`} replace />;
 const onSubmit = async (values) => {
  setBusy(true);
  try { await login(portal, values); navigate(location.state?.from?.pathname || `/${portal}`, { replace: true }); }
  catch (error) { toast.error(error.response?.data?.message || (!error.response ? 'Could not reach the API. Check that the backend is running and allows this frontend URL.' : 'Sign in failed. Check your details and portal.')); }
  finally { setBusy(false); }
 };
 const accent = { admin: 'indigo', operator: 'emerald', support: 'amber', customer: 'sky' }[portal];
 const classes = { indigo: 'bg-indigo-600 hover:bg-indigo-700 text-indigo-700', emerald: 'bg-emerald-600 hover:bg-emerald-700 text-emerald-700', amber: 'bg-amber-500 hover:bg-amber-600 text-amber-700', sky: 'bg-sky-600 hover:bg-sky-700 text-sky-700' }[accent];
 return <main className="relative min-h-screen bg-slate-50 md:grid md:grid-cols-2"><div className="absolute right-5 top-5 z-10"><ThemeToggle/></div>
  <section className={`hidden flex-col justify-between p-12 text-white md:flex ${classes.split(' ')[0]}`}>
   <div className="flex items-center gap-3 text-xl font-bold"><span className="rounded-xl bg-white/15 p-2"><Zap size={22}/></span> UtilityCare</div>
   <div><div className="mb-5 flex gap-3">{icons.map((Icon,i)=><span key={i} className="rounded-full bg-white/15 p-3"><Icon size={20}/></span>)}</div><h1 className="max-w-md text-4xl font-bold leading-tight">Essential services, managed with care.</h1><p className="mt-4 max-w-sm text-white/80">{config.subtitle}</p></div>
   <p className="text-sm text-white/70">Electricity · Water · LPG</p>
  </section>
  <section className="flex min-h-screen items-center justify-center px-5 py-12">
   <div className="w-full max-w-md">
    <div className="mb-8 md:hidden"><div className={`mb-5 inline-flex rounded-xl p-3 text-white ${classes.split(' ')[0]}`}><Zap/></div><p className="font-semibold text-slate-900">UtilityCare</p></div>
    <p className={`text-sm font-semibold uppercase tracking-wider ${classes.split(' ').at(-1)}`}>{config.label} portal</p>
    <h2 className="mt-2 text-3xl font-bold text-slate-900">Welcome back</h2><p className="mt-2 text-slate-500">Sign in to continue to your account.</p>
    <form className="mt-8 space-y-5" onSubmit={handleSubmit(onSubmit)} noValidate>
     <label className="block"><span className="mb-2 block text-sm font-medium text-slate-700">Email address</span><input autoComplete="username" type="email" {...register('email')} className="field" placeholder="you@example.com"/>{errors.email&&<span className="mt-1 block text-sm text-rose-600">{errors.email.message}</span>}</label>
     <label className="block"><span className="mb-2 block text-sm font-medium text-slate-700">Password</span><span className="relative block"><input autoComplete="current-password" type={showPassword?'text':'password'} {...register('password')} className="field pr-12" placeholder="Enter your password"/><button type="button" aria-label={showPassword?'Hide password':'Show password'} onClick={()=>setShowPassword(!showPassword)} className="absolute inset-y-0 right-3 text-slate-500">{showPassword?<EyeOff size={18}/>:<Eye size={18}/>}</button></span>{errors.password&&<span className="mt-1 block text-sm text-rose-600">{errors.password.message}</span>}</label>
     <div className="flex justify-end"><Link className="text-sm font-medium text-slate-600 hover:underline" to="/forgot-password">Forgot password?</Link></div>
     <button disabled={busy} className={`w-full rounded-lg px-4 py-3 font-semibold text-white transition disabled:opacity-60 ${classes.split(' ').slice(0,2).join(' ')}`}>{busy?'Signing in…':'Sign in'}</button>
    </form>
    {portal==='customer'&&<p className="mt-6 text-center text-sm text-slate-600">New customer? <Link to="/customer/register" className="font-semibold text-sky-700 hover:underline">Create an account</Link></p>}
    <p className="mt-5 text-center text-sm text-slate-600"><Link to="/" className="font-semibold text-sky-700 hover:underline">Choose a different portal</Link></p>
    <p className="mt-8 text-center text-xs text-slate-400">Secure access · Your account is protected</p>
   </div>
  </section>
 </main>;
}

