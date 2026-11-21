import { useCallback, useEffect, useState } from 'react';
import { Check, X } from 'lucide-react';
import toast from 'react-hot-toast';
import { adminApi } from '../../api/adminApi';
import DataTable from '../../components/common/DataTable';
import StatusBadge from '../../components/common/StatusBadge';
export default function AdminApprovalsPage(){
 const [tab,setTab]=useState('registrations');const [registrations,setRegistrations]=useState({items:[],page:0,size:20,total:0});const [requests,setRequests]=useState({items:[],page:0,size:20,total:0});const [q,setQ]=useState('');const [page,setPage]=useState(0);
 const load=useCallback(()=>{const p={q,page,size:20};return Promise.all([adminApi.registrations(p),adminApi.connectionRequests({...p,status:'PENDING'})]).then(([a,b])=>{setRegistrations(a.data.data);setRequests(b.data.data);}).catch(()=>toast.error('Could not load approval queue.'));},[q,page]);
 useEffect(()=>{load();},[load]);
 const decide=async(type,id,decision)=>{try{if(type==='registration')await adminApi.decideRegistration(id,decision);else await adminApi.decideConnection(id,decision);toast.success(`Request ${decision.toLowerCase()}.`);await load();}catch(e){toast.error(e.response?.data?.message||'Could not update request.');}};
 const actions=(type)=>(row)=><div className="flex gap-2"><button title="Approve" onClick={()=>decide(type,row.userId??row.id,'APPROVED')} className="rounded-md bg-emerald-50 p-2 text-emerald-700"><Check size={16}/></button><button title="Reject" onClick={()=>decide(type,row.userId??row.id,'REJECTED')} className="rounded-md bg-rose-50 p-2 text-rose-700"><X size={16}/></button></div>;
 const registrationColumns=[{key:'customerNumber',label:'Customer ID'},{key:'fullName',label:'Name'},{key:'email',label:'Email'},{key:'phone',label:'Phone'},{key:'createdAt',label:'Submitted',render:r=>new Date(r.createdAt).toLocaleDateString()},{key:'actions',label:'Decision',render:actions('registration')}];
 const requestColumns=[{key:'id',label:'Request'},{key:'requestType',label:'Type'},{key:'customerNumber',label:'Customer ID'},{key:'customerName',label:'Customer'},{key:'service',label:'Service'},{key:'status',label:'Status',render:r=><StatusBadge status={r.status}/>},{key:'createdAt',label:'Submitted',render:r=>new Date(r.createdAt).toLocaleDateString()},{key:'actions',label:'Decision',render:actions('connection')}];
 const current=tab==='registrations'?registrations:requests;
 return <div><p className="text-sm text-slate-500">Administration / Approvals</p><h1 className="mt-2 text-2xl font-bold text-slate-900">Approval queue</h1><p className="mt-1 text-sm text-slate-500">Review customer registrations and connection requests</p><div className="my-5 flex flex-wrap items-center justify-between gap-3"><div className="flex gap-2">{[['registrations','Customer registrations'],['connections','Connection requests']].map(([key,label])=><button key={key} onClick={()=>{setTab(key);setPage(0);}} className={`rounded-lg px-4 py-2 text-sm font-medium ${tab===key?'bg-indigo-600 text-white':'bg-white text-slate-600 ring-1 ring-slate-200'}`}>{label}</button>)}</div><input className="field max-w-sm" placeholder="Search approvals" value={q} onChange={e=>{setQ(e.target.value);setPage(0);}}/></div><DataTable columns={tab==='registrations'?registrationColumns:requestColumns} rows={current.items||[]} page={current.page||0} size={current.size||20} total={current.total||0} onPage={setPage}/></div>;
}

