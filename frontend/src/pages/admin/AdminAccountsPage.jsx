import { useCallback, useEffect, useState } from 'react';
import { KeyRound, Lock, Pencil, Plus, Power, Search, Trash2, Unlock } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import toast from 'react-hot-toast';
import { adminApi } from '../../api/adminApi';
import DataTable from '../../components/common/DataTable';
import Modal from '../../components/common/Modal';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import FormInput from '../../components/common/FormInput';
import StatusBadge from '../../components/common/StatusBadge';
const schema=yup.object({fullName:yup.string().max(160).required('Enter a name'),email:yup.string().email().required('Enter a valid email'),phone:yup.string().max(30),role:yup.string().required(),password:yup.string().when('$editing',{is:false,then:s=>s.min(10).max(72).required('Initial password is required'),otherwise:s=>s.notRequired()})});
function AccountEditor({open,account,onClose,onSave}){
 const form=useForm({resolver:yupResolver(schema),context:{editing:Boolean(account)},defaultValues:account?{...account,password:''}:{fullName:'',email:'',phone:'',role:'OPERATOR',password:''}});
 useEffect(()=>{form.reset(account?{...account,password:''}:{fullName:'',email:'',phone:'',role:'OPERATOR',password:''});},[account,open]);
 return <Modal open={open} title={account?'Edit account':'Create staff or customer account'} onClose={onClose}><form className="space-y-4" onSubmit={form.handleSubmit(onSave)}>
  <FormInput label="Full name" {...form.register('fullName')} error={form.formState.errors.fullName?.message}/>
  <FormInput label="Email" type="email" {...form.register('email')} error={form.formState.errors.email?.message}/>
  <FormInput label="Phone" {...form.register('phone')}/>
  {!account&&<label className="block text-sm font-medium text-slate-700">Account role<select className="field mt-1.5" {...form.register('role')}><option value="ADMIN">Admin</option><option value="OPERATOR">Operator</option><option value="HELP_SUPPORT">Customer Service / Help Support</option><option value="CUSTOMER">Customer</option></select></label>}
  {!account&&<FormInput label="Initial password" type="password" autoComplete="new-password" {...form.register('password')} error={form.formState.errors.password?.message}/>}
  <div className="flex justify-end gap-3 pt-2"><button type="button" className="rounded-lg border px-4 py-2" onClick={onClose}>Cancel</button><button className="rounded-lg bg-indigo-600 px-4 py-2 font-semibold text-white">{account?'Save changes':'Create account'}</button></div>
 </form></Modal>;
}
export default function AdminAccountsPage(){
 const [data,setData]=useState({items:[],page:0,size:20,total:0});const [q,setQ]=useState('');const [role,setRole]=useState('');const [status,setStatus]=useState('');const [sort,setSort]=useState('createdAt');const [direction,setDirection]=useState('DESC');const [editing,setEditing]=useState(null);const [create,setCreate]=useState(false);const [confirm,setConfirm]=useState(null);const [passwordAccount,setPasswordAccount]=useState(null);const [password,setPassword]=useState('');
 const load=useCallback(()=>adminApi.accounts({q,role,status,page:data.page,size:data.size,sort,direction}).then(r=>setData(r.data.data)).catch(()=>toast.error('Could not load accounts.')),[q,role,status,data.page,data.size,sort,direction]);
 useEffect(()=>{load();},[load]);
 const save=async values=>{try{if(editing){await adminApi.updateAccount(editing.userId,{email:values.email,fullName:values.fullName,phone:values.phone||null});toast.success('Account updated.');setEditing(null);}else{await adminApi.createAccount(values);toast.success('Account created.');setCreate(false);}await load();}catch(e){toast.error(e.response?.data?.message||'Could not save account.');}};
 const statusAction=async(row,next)=>{try{await adminApi.setAccountStatus(row.userId,next);toast.success(`Account ${next.toLowerCase()}.`);await load();}catch{toast.error('Could not change account status.');}};
 const columns=[
  {key:'fullName',label:'Name',sortable:true},{key:'email',label:'Email',sortable:true},{key:'role',label:'Role',sortable:true,render:r=>r.role.replace('_',' ')},{key:'status',label:'Status',sortable:true,render:r=><StatusBadge status={r.status}/>},{key:'phone',label:'Phone'},{key:'actions',label:'Actions',render:r=><div className="flex items-center gap-1">
   <button aria-label="Edit account" title="Edit" onClick={()=>setEditing(r)} className="rounded p-2 text-slate-500 hover:bg-slate-100"><Pencil size={16}/></button>
   {r.status==='LOCKED'?<button title="Unlock" onClick={()=>statusAction(r,'ACTIVE')} className="rounded p-2 text-emerald-700 hover:bg-emerald-50"><Unlock size={16}/></button>:<button title="Lock" onClick={()=>statusAction(r,'LOCKED')} className="rounded p-2 text-amber-700 hover:bg-amber-50"><Lock size={16}/></button>}
   {r.status==='ACTIVE'?<button title="Deactivate" onClick={()=>statusAction(r,'INACTIVE')} className="rounded p-2 text-slate-500 hover:bg-slate-100"><Power size={16}/></button>:<button title="Activate" onClick={()=>statusAction(r,'ACTIVE')} className="rounded p-2 text-emerald-700 hover:bg-emerald-50"><Power size={16}/></button>}
   <button title="Reset password" onClick={()=>{setPasswordAccount(r);setPassword('');}} className="rounded p-2 text-indigo-600 hover:bg-indigo-50"><KeyRound size={16}/></button>
   <button title="Remove account" onClick={()=>setConfirm(r)} className="rounded p-2 text-rose-600 hover:bg-rose-50"><Trash2 size={16}/></button>
  </div>}
 ];
 return <div><p className="text-sm text-slate-500">Administration / Accounts</p><div className="mt-2 flex flex-wrap items-end justify-between gap-3"><div><h1 className="text-2xl font-bold text-slate-900">Account management</h1><p className="mt-1 text-sm text-slate-500">Manage operators, support staff, and customers</p></div><button onClick={()=>setCreate(true)} className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white"><Plus size={17}/>Create account</button></div>
  <div className="my-5 flex flex-wrap gap-3"><label className="relative min-w-60 flex-1"><Search size={16} className="absolute left-3 top-3 text-slate-400"/><input value={q} onChange={e=>{setQ(e.target.value);setData(d=>({...d,page:0}));}} className="field pl-9" placeholder="Search name, email, customer number"/></label><select className="field w-auto min-w-40" value={role} onChange={e=>{setRole(e.target.value);setData(d=>({...d,page:0}));}}><option value="">All roles</option><option value="ADMIN">Admins</option><option value="OPERATOR">Operators</option><option value="HELP_SUPPORT">Customer Service</option><option value="CUSTOMER">Customers</option></select><select className="field w-auto min-w-36" value={status} onChange={e=>{setStatus(e.target.value);setData(d=>({...d,page:0}));}}><option value="">All statuses</option>{['ACTIVE','INACTIVE','LOCKED','PENDING_APPROVAL'].map(x=><option key={x}>{x}</option>)}</select></div>
  <DataTable columns={columns} rows={data.items||[]} page={data.page||0} size={data.size||20} total={data.total||0} sort={sort} direction={direction} onPage={page=>setData(d=>({...d,page}))} onSort={key=>{setSort(key);setDirection(d=>d==='ASC'?'DESC':'ASC');}}/>
  <AccountEditor open={create||Boolean(editing)} account={editing} onClose={()=>{setCreate(false);setEditing(null);}} onSave={save}/>
  <Modal open={Boolean(passwordAccount)} title={`Reset password for ${passwordAccount?.email||''}`} onClose={()=>setPasswordAccount(null)}><form onSubmit={async e=>{e.preventDefault();try{await adminApi.resetPassword(passwordAccount.userId,password);toast.success('Password reset; current sessions were revoked.');setPasswordAccount(null);}catch(error){toast.error(error.response?.data?.message||'Could not reset password.');}}} className="space-y-4"><FormInput label="New password" minLength={10} maxLength={72} type="password" autoComplete="new-password" required value={password} onChange={e=>setPassword(e.target.value)}/><div className="flex justify-end gap-3"><button type="button" className="rounded-lg border px-4 py-2" onClick={()=>setPasswordAccount(null)}>Cancel</button><button className="rounded-lg bg-indigo-600 px-4 py-2 font-semibold text-white">Reset password</button></div></form></Modal>
  <ConfirmDialog open={Boolean(confirm)} title="Remove account" message={`This will deactivate ${confirm?.email} and prevent future sign-ins. Historical records will remain.`} onCancel={()=>setConfirm(null)} onConfirm={async()=>{try{await adminApi.deleteAccount(confirm.userId);toast.success('Account deactivated.');setConfirm(null);await load();}catch{toast.error('Could not remove account.');}}}/>
 </div>;
}

