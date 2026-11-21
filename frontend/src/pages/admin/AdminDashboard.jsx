import { useEffect, useState } from 'react';
import { Activity, BadgeIndianRupee, CircleAlert, ClipboardCheck, Headset, Users } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { adminApi } from '../../api/adminApi';
import StatCard from '../../components/common/StatCard';
const currency=new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0});
const palette=['#0f766e','#4f46e5','#f59e0b','#e11d48'];
export default function AdminDashboard(){
 const [data,setData]=useState(null);const [busy,setBusy]=useState(true);
 useEffect(()=>{adminApi.dashboard().then(r=>setData(r.data.data)).catch(()=>toast.error('Could not load dashboard metrics.')).finally(()=>setBusy(false));},[]);
 if(busy)return <div className="grid min-h-64 place-items-center text-slate-500">Loading dashboard…</div>;
 if(!data)return <div className="card">Dashboard metrics are unavailable.</div>;
 const cards=[
  ['Customers',data.customers,Users,'indigo'],
  ['Operators',data.operators,Activity,'teal'],
  ['Support staff',data.supportStaff,Headset,'amber'],
  ['Revenue today',currency.format(data.revenueToday||0),BadgeIndianRupee,'teal'],
  ['Revenue this month',currency.format(data.revenueMonth||0),BadgeIndianRupee,'indigo'],
  ['Revenue this year',currency.format(data.revenueYear||0),BadgeIndianRupee,'amber'],
  ['Bills awaiting payment',data.pendingBills,ClipboardCheck,'amber'],
  ['Overdue bills',data.overdueBills,CircleAlert,'rose'],
  ['Open tickets',data.openTickets,Headset,'rose'],
  ['Registrations to review',data.pendingRegistrations,Users,'indigo']
 ];
 return <div><p className="text-sm text-slate-500">Home / Overview</p><div className="mt-2 flex flex-wrap items-end justify-between gap-3"><div><h1 className="text-2xl font-bold text-slate-900">Administration dashboard</h1><p className="mt-1 text-sm text-slate-500">Utility service operations summary</p></div><Link to="/admin/approvals" className="rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-indigo-700">Review approvals</Link></div>
  <section className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-5">{cards.map(([label,value,icon,color])=><StatCard key={label} label={label} value={value??0} icon={icon} color={color}/>)}</section>
  <section className="mt-5 grid gap-5 xl:grid-cols-2"><article className="card"><h2 className="font-semibold text-slate-900">Monthly revenue</h2><p className="mt-1 text-sm text-slate-500">Successful payments by month</p><div className="mt-4 h-72"><ResponsiveContainer width="100%" height="100%"><BarChart data={[...(data.monthlyRevenue||[])].reverse()} margin={{top:8,right:8,left:8,bottom:4}}><CartesianGrid strokeDasharray="3 3" vertical={false}/><XAxis dataKey="month"/><YAxis tickFormatter={v=>currency.format(v)}/><Tooltip formatter={v=>currency.format(v)}/><Bar dataKey="revenue" name="Revenue" fill="#4f46e5" radius={[5,5,0,0]}/></BarChart></ResponsiveContainer></div></article>
   <article className="card"><h2 className="font-semibold text-slate-900">Consumption by service</h2><p className="mt-1 text-sm text-slate-500">Recorded units across all meter readings</p><div className="mt-4 h-72"><ResponsiveContainer width="100%" height="100%"><PieChart><Pie data={data.consumptionByService||[]} dataKey="consumption" nameKey="service" cx="50%" cy="50%" outerRadius={95} label>{(data.consumptionByService||[]).map((entry,index)=><Cell key={entry.service} fill={palette[index%palette.length]}/>)}</Pie><Tooltip/><Legend/></PieChart></ResponsiveContainer></div></article></section>
 </div>;
}

