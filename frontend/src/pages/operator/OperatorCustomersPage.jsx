import { useCallback, useEffect, useState } from 'react';
import { Activity, Search } from 'lucide-react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { operatorApi } from '../../api/operatorApi';
import DataTable from '../../components/common/DataTable';
import StatusBadge from '../../components/common/StatusBadge';
export default function OperatorCustomersPage(){
 const [q,setQ]=useState('');const [page,setPage]=useState(0);const [sort,setSort]=useState('fullName');const [direction,setDirection]=useState('ASC');const [data,setData]=useState({items:[],page:0,size:20,total:0});
 const load=useCallback(()=>operatorApi.customers({q,page,size:20,sort,direction}).then(r=>setData(r.data.data)).catch(()=>toast.error('Could not load assigned customers.')),[q,page,sort,direction]);
 useEffect(()=>{load();},[load]);
 const columns=[{key:'customerNumber',label:'Customer ID',sortable:true},{key:'fullName',label:'Customer',sortable:true},{key:'phone',label:'Phone'},{key:'service',label:'Service',sortable:true},{key:'meterNumber',label:'Meter',sortable:true},{key:'address',label:'Address'},{key:'connectionStatus',label:'Connection',render:r=><StatusBadge status={r.connectionStatus}/>},{key:'actions',label:'Action',render:r=><Link to={`/operator/readings?connectionId=${r.connectionId}`} className="inline-flex items-center gap-2 rounded-md bg-indigo-50 px-3 py-2 text-xs font-medium text-indigo-700"><Activity size={14}/>Record reading</Link>}];
 return <div><p className="text-sm text-slate-500">Operations / Customers</p><h1 className="mt-2 text-2xl font-bold text-slate-900">Assigned customers</h1><p className="mt-1 text-sm text-slate-500">Only customers assigned directly or through your zones appear here.</p><label className="relative my-5 block max-w-2xl"><Search size={16} className="absolute left-3 top-3 text-slate-400"/><input className="field pl-9" value={q} placeholder="Search customer name, ID, phone, meter number or service" onChange={e=>{setQ(e.target.value);setPage(0);}}/></label><DataTable columns={columns} rows={data.items||[]} page={data.page||0} size={data.size||20} total={data.total||0} sort={sort} direction={direction} onPage={setPage} onSort={key=>{setSort(key);setDirection(v=>v==='ASC'?'DESC':'ASC');}}/></div>;
}

