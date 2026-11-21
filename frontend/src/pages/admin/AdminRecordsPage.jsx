import { useCallback, useEffect, useState } from 'react';
import { Download, Search } from 'lucide-react';
import toast from 'react-hot-toast';
import client from '../../api/client';
import { adminApi } from '../../api/adminApi';
import DataTable from '../../components/common/DataTable';
import StatusBadge from '../../components/common/StatusBadge';
const tabs=[['bills','Bills'],['payments','Payments'],['readings','Meter readings'],['tickets','Tickets'],['connectionRequests','Connection requests'],['audit','Audit log']];
const fields={
 bills:[['billNumber','Bill number'],['cycle','Cycle'],['customerName','Customer'],['service','Service'],['meterNumber','Meter'],['dueDate','Due date'],['totalAmount','Total'],['paidAmount','Paid'],['status','Status']],
 payments:[['transactionId','Transaction'],['billNumber','Bill number'],['customerName','Customer'],['amount','Amount'],['method','Method'],['paidAt','Paid at'],['status','Status']],
 readings:[['cycle','Cycle'],['customerName','Customer'],['service','Service'],['meterNumber','Meter'],['previousReading','Previous'],['currentReading','Current'],['readingDate','Date'],['rollover','Rollover']],
 tickets:[['ticketNumber','Ticket'],['subject','Subject'],['customerName','Customer'],['service','Service'],['priority','Priority'],['status','Status'],['dueAt','Due by']],
 connectionRequests:[['id','Request'],['requestType','Type'],['customerName','Customer'],['service','Service'],['status','Status'],['createdAt','Submitted']],
 audit:[['createdAt','When'],['actor','Who'],['action','Action'],['entityType','Record'],['entityId','ID'],['ipAddress','IP'],['oldValue','Old value'],['newValue','New value']]
};
export default function AdminRecordsPage(){
 const [resource,setResource]=useState('bills');const [query,setQuery]=useState('');const [status,setStatus]=useState('');const [page,setPage]=useState(0);const [data,setData]=useState({items:[],page:0,size:20,total:0});const [sort,setSort]=useState('dueDate');const [direction,setDirection]=useState('DESC');const [busy,setBusy]=useState(false);
 const load=useCallback(()=>adminApi.list(resource,{q:query,status,page,size:20,sort,direction}).then(r=>setData(r.data.data)).catch(()=>toast.error('Could not load records.')),[resource,query,status,page,sort,direction]);
 useEffect(()=>{load();},[load]);
 const changeResource=value=>{setResource(value);setPage(0);setStatus('');setSort(value==='bills'?'dueDate':value==='payments'?'paidAt':value==='readings'?'readingDate':'createdAt');};
 const exportFile=async format=>{setBusy(true);try{const url=adminApi.exportUrl(resource,format,{q:query,status});const res=await client.get(url,{responseType:'blob'});const blob=new Blob([res.data],{type:format==='csv'?'text/csv':'application/pdf'});const link=document.createElement('a');link.href=URL.createObjectURL(blob);link.download=`${resource}.${format}`;link.click();URL.revokeObjectURL(link.href);toast.success(`${format.toUpperCase()} export downloaded.`);}catch{toast.error('Could not export records.');}finally{setBusy(false);}};
 const columns=(fields[resource]||[]).map(([key,label])=>({key,label,sortable:['billNumber','cycle','dueDate','totalAmount','status','transactionId','amount','paidAt','readingDate','ticketNumber','priority','createdAt','action','entityType'].includes(key),render:row=>key==='status'?<StatusBadge status={row[key]}/>:key==='oldValue'||key==='newValue'?<span className="block max-w-56 truncate font-mono text-xs" title={typeof row[key]==='string'?row[key]:JSON.stringify(row[key])}>{typeof row[key]==='string'?row[key]:JSON.stringify(row[key])||'—'}</span>:row[key]===true?'Yes':row[key]===false?'No':row[key]??'—'}));
 return <div><p className="text-sm text-slate-500">Administration / Records</p><div className="mt-2 flex flex-wrap items-end justify-between gap-3"><div><h1 className="text-2xl font-bold text-slate-900">Operational records</h1><p className="mt-1 text-sm text-slate-500">Search, filter, sort, and export service records</p></div><div className="flex gap-2"><button disabled={busy} onClick={()=>exportFile('csv')} className="inline-flex items-center gap-2 rounded-lg border bg-white px-3 py-2.5 text-sm font-semibold"><Download size={16}/>CSV</button><button disabled={busy} onClick={()=>exportFile('pdf')} className="inline-flex items-center gap-2 rounded-lg border bg-white px-3 py-2.5 text-sm font-semibold"><Download size={16}/>PDF</button></div></div>
  <div className="my-5 flex flex-wrap gap-2">{tabs.map(([key,label])=><button key={key} onClick={()=>changeResource(key)} className={`rounded-lg px-3 py-2 text-sm font-medium ${resource===key?'bg-indigo-600 text-white':'bg-white text-slate-600 ring-1 ring-slate-200'}`}>{label}</button>)}</div>
  <div className="mb-4 flex flex-wrap gap-3"><label className="relative min-w-60 flex-1"><Search size={16} className="absolute left-3 top-3 text-slate-400"/><input className="field pl-9" placeholder="Search records" value={query} onChange={e=>{setQuery(e.target.value);setPage(0);}}/></label>{['bills','payments','tickets','connectionRequests'].includes(resource)&&<select className="field w-auto min-w-40" value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}><option value="">All statuses</option>{(resource==='bills'?['GENERATED','PARTIALLY_PAID','PAID','OVERDUE','CANCELLED']:resource==='payments'?['SUCCESS','FAILED','PENDING']:resource==='tickets'?['OPEN','IN_PROGRESS','RESOLVED','CLOSED']:['PENDING','APPROVED','REJECTED']).map(x=><option key={x}>{x}</option>)}</select>}</div>
  <DataTable columns={columns} rows={data.items||[]} page={data.page||page} size={data.size||20} total={data.total||0} sort={sort} direction={direction} onPage={setPage} onSort={key=>{setSort(key);setDirection(x=>x==='ASC'?'DESC':'ASC');}}/>
 </div>;
}

