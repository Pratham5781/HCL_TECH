import { useEffect, useState } from 'react';
import { Download } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import toast from 'react-hot-toast';
import { adminApi } from '../../api/adminApi';
import DataTable from '../../components/common/DataTable';
const tabs=[['defaulters','Defaulters'],['operatorPerformance','Operator performance'],['ticketResolution','Ticket resolution']];
export default function AdminReportsPage(){
 const [report,setReport]=useState(null);const [tab,setTab]=useState('defaulters');const [busy,setBusy]=useState(true);
 useEffect(()=>{adminApi.reports().then(r=>setReport(r.data.data)).catch(()=>toast.error('Could not load reports.')).finally(()=>setBusy(false));},[]);
 if(busy)return <div className="card">Loading reports…</div>;
 if(!report)return <div className="card">Reports are unavailable.</div>;
 const revenue=[...(report.monthlyRevenue||[])].reverse();
 const columns=tab==='defaulters'?[{key:'customerNumber',label:'Customer ID'},{key:'customerName',label:'Customer'},{key:'billNumber',label:'Bill'},{key:'service',label:'Service'},{key:'dueDate',label:'Due date'},{key:'amountDue',label:'Amount due'}]:tab==='operatorPerformance'?[{key:'operatorName',label:'Operator'},{key:'assignedCustomers',label:'Assigned customers'},{key:'readingsRecorded',label:'Readings recorded'}]:[{key:'service',label:'Service'},{key:'resolvedTickets',label:'Resolved tickets'},{key:'averageHours',label:'Average resolution (hours)'}];
 const rows=report[tab]||[];
 const download=()=>{try{const rows=report.defaulters||[];const csv=['Customer ID,Name,Bill,Service,Due date,Amount due',...rows.map(r=>[r.customerNumber,r.customerName,r.billNumber,r.service,r.dueDate,r.amountDue].map(x=>`"${String(x??'').replaceAll('"','""')}"`).join(','))].join('\r\n');const url=URL.createObjectURL(new Blob([csv],{type:'text/csv'}));const a=document.createElement('a');a.href=url;a.download='defaulters.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}catch{toast.error('Could not export the defaulters list.');}};
 return <div><p className="text-sm text-slate-500">Administration / Reports</p><h1 className="mt-2 text-2xl font-bold text-slate-900">Reports</h1><p className="mt-1 text-sm text-slate-500">Revenue, consumption, defaulters, operator performance, and ticket resolution</p><article className="card mt-5"><h2 className="font-semibold">Monthly revenue</h2><div className="mt-4 h-72"><ResponsiveContainer width="100%" height="100%"><BarChart data={revenue}><CartesianGrid strokeDasharray="3 3" vertical={false}/><XAxis dataKey="month"/><YAxis/><Tooltip/><Bar dataKey="revenue" fill="#0f766e" radius={[5,5,0,0]}/></BarChart></ResponsiveContainer></div></article>
  <div className="my-5 flex flex-wrap items-center justify-between gap-3"><div className="flex gap-2">{tabs.map(([key,label])=><button key={key} onClick={()=>setTab(key)} className={`rounded-lg px-4 py-2 text-sm font-medium ${tab===key?'bg-indigo-600 text-white':'bg-white text-slate-600 ring-1 ring-slate-200'}`}>{label}</button>)}</div>{tab==='defaulters'&&<button onClick={download} className="inline-flex items-center gap-2 rounded-lg border bg-white px-3 py-2 text-sm"><Download size={16}/>Export defaulters CSV</button>}</div>
  <DataTable columns={columns} rows={rows} size={Math.max(rows.length,1)} total={rows.length}/>
 </div>;
}

