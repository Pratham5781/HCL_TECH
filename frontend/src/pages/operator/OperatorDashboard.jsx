import { useEffect, useState } from 'react';
import { Activity, BookOpenCheck, ClipboardList, Flame, Users } from 'lucide-react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { operatorApi } from '../../api/operatorApi';
import StatCard from '../../components/common/StatCard';
export default function OperatorDashboard(){
 const [stats,setStats]=useState(null);
 useEffect(()=>{Promise.all([operatorApi.customers({page:0,size:1}),operatorApi.readings({page:0,size:1}),operatorApi.bills({page:0,size:1}),operatorApi.lpgBookings({page:0,size:1})]).then(([c,r,b,l])=>setStats({customers:c.data.data.total,readings:r.data.data.total,bills:b.data.data.total,lpg:l.data.data.total})).catch(()=>toast.error('Could not load operator summary.'));},[]);
 return <div><p className="text-sm text-slate-500">Operations / Overview</p><h1 className="mt-2 text-2xl font-bold text-slate-900">Field operations</h1><p className="mt-1 text-sm text-slate-500">Assigned customers, meter readings, billing, and LPG deliveries</p><section className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4"><StatCard label="Assigned connections" value={stats?.customers??'—'} icon={Users} color="indigo"/><StatCard label="Recorded readings" value={stats?.readings??'—'} icon={Activity} color="teal"/><StatCard label="Generated bills" value={stats?.bills??'—'} icon={BookOpenCheck} color="amber"/><StatCard label="LPG bookings" value={stats?.lpg??'—'} icon={Flame} color="rose"/></section><section className="mt-6 grid gap-4 md:grid-cols-3"><Link to="/operator/customers" className="card hover:border-teal-300"><Users className="text-teal-700"/><h2 className="mt-3 font-semibold">Assigned customers</h2><p className="mt-1 text-sm text-slate-500">Search customers, meters and service connections.</p></Link><Link to="/operator/readings" className="card hover:border-indigo-300"><Activity className="text-indigo-700"/><h2 className="mt-3 font-semibold">Meter readings</h2><p className="mt-1 text-sm text-slate-500">Record cycle readings and meter reset evidence.</p></Link><Link to="/operator/bills" className="card hover:border-amber-300"><ClipboardList className="text-amber-700"/><h2 className="mt-3 font-semibold">Billing and LPG</h2><p className="mt-1 text-sm text-slate-500">Generate cycle bills and track cylinder deliveries.</p></Link></section></div>;
}

