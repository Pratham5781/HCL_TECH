import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import toast from 'react-hot-toast';
import client from '../../api/client';
const schema=yup.object({fullName:yup.string().max(160).required('Name is required'),email:yup.string().email().required(),phone:yup.string().max(30),password:yup.string().min(10).max(72).required()});
export default function RegisterPage(){
 const nav=useNavigate();const [busy,setBusy]=useState(false);const {register,handleSubmit,formState:{errors}}=useForm({resolver:yupResolver(schema)});
 const submit=async(v)=>{setBusy(true);try{await client.post('/api/auth/customer/register',v);toast.success('Registration submitted for approval.');nav('/customer/login');}catch(e){toast.error(e.response?.data?.message||(!e.response?'Could not reach the API. Check that the backend is running and allows this frontend URL.':'Registration failed.'));}finally{setBusy(false);}};
 return <main className="grid min-h-screen place-items-center bg-slate-50 p-5"><section className="card w-full max-w-md"><h1 className="text-2xl font-bold">Create your customer account</h1><p className="mt-2 text-sm text-slate-500">New accounts are reviewed before sign-in is enabled.</p><form className="mt-6 space-y-4" onSubmit={handleSubmit(submit)}>{[['fullName','Full name','text'],['email','Email address','email'],['phone','Phone number','tel'],['password','Password','password']].map(([name,label,type])=><label key={name} className="block text-sm font-medium">{label}<input className="field mt-2" type={type} autoComplete={name==='password'?'new-password':name==='email'?'email':'name'} {...register(name)}/>{errors[name]&&<small className="text-rose-600">{errors[name].message}</small>}</label>)}<button disabled={busy} className="w-full rounded-lg bg-sky-700 px-4 py-3 font-semibold text-white disabled:opacity-60">{busy?'Submitting…':'Register'}</button></form><Link className="mt-5 block text-center text-sm text-sky-700" to="/customer/login">Back to sign in</Link></section></main>;
}

