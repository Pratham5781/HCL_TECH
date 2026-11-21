import { useEffect, useState } from 'react';
import { Save } from 'lucide-react';
import toast from 'react-hot-toast';
import { adminApi } from '../../api/adminApi';
export default function AdminAssignmentsPage(){
 const [data,setData]=useState(null);const [operator,setOperator]=useState('');const [zones,setZones]=useState([]);const [customers,setCustomers]=useState([]);const [busy,setBusy]=useState(false);
 const load=()=>adminApi.assignments().then(r=>{const x=r.data.data;setData(x);if(!operator&&x.operators.length)setOperator(String(x.operators[0].userId));}).catch(()=>toast.error('Could not load assignments.'));
 useEffect(()=>{load();},[]);
 useEffect(()=>{if(!data||!operator)return;const id=Number(operator);setZones(data.zoneAssignments.filter(x=>x.operatorId===id).map(x=>String(x.zoneId)));setCustomers(data.customerAssignments.filter(x=>x.operatorId===id).map(x=>String(x.customerId)));},[data,operator]);
 const toggle=(items,set,id)=>set(items.includes(String(id))?items.filter(x=>x!==String(id)):[...items,String(id)]);
 const save=async kind=>{setBusy(true);try{if(kind==='zones')await adminApi.assignZones(Number(operator),zones.map(Number));else await adminApi.assignCustomers(Number(operator),customers.map(Number));toast.success('Assignments saved.');await load();}catch(e){toast.error(e.response?.data?.message||'Could not save assignments.');}finally{setBusy(false);}};
 if(!data)return <div className="card">Loading operator assignments…</div>;
 const selected=data.operators.find(x=>String(x.userId)===operator);
 return <div><p className="text-sm text-slate-500">Administration / Assignments</p><h1 className="mt-2 text-2xl font-bold text-slate-900">Operator assignments</h1><p className="mt-1 text-sm text-slate-500">Assign service zones and individual customers to an operator</p><label className="mt-5 block max-w-lg text-sm font-medium text-slate-700">Operator<select className="field mt-1.5" value={operator} onChange={e=>setOperator(e.target.value)}>{data.operators.map(o=><option key={o.userId} value={o.userId}>{o.fullName} · {o.email}</option>)}</select></label>
  <div className="mt-5 grid gap-5 xl:grid-cols-2"><section className="card"><div className="flex items-center justify-between"><div><h2 className="font-semibold">Assigned zones</h2><p className="mt-1 text-sm text-slate-500">Zone assignment grants area coverage</p></div><button disabled={!selected||busy} onClick={()=>save('zones')} className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50"><Save size={16}/>Save zones</button></div><div className="mt-4 space-y-2">{data.zones.map(z=><label key={z.id} className="flex cursor-pointer items-center gap-3 rounded-lg border border-slate-100 p-3 text-sm"><input type="checkbox" checked={zones.includes(String(z.id))} onChange={()=>toggle(zones,setZones,z.id)}/><span className="font-medium">{z.name}</span><span className="text-slate-500">{z.description}</span></label>)}</div></section>
   <section className="card"><div className="flex items-center justify-between"><div><h2 className="font-semibold">Assigned customers</h2><p className="mt-1 text-sm text-slate-500">Direct customer assignments</p></div><button disabled={!selected||busy} onClick={()=>save('customers')} className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50"><Save size={16}/>Save customers</button></div><div className="mt-4 max-h-96 space-y-2 overflow-y-auto">{data.customers.map(c=><label key={c.userId} className="flex cursor-pointer items-center gap-3 rounded-lg border border-slate-100 p-3 text-sm"><input type="checkbox" checked={customers.includes(String(c.userId))} onChange={()=>toggle(customers,setCustomers,c.userId)}/><span className="font-medium">{c.fullName}</span><span className="text-slate-500">{c.customerNumber} · {c.email}</span></label>)}</div></section>
  </div>
 </div>;
}

