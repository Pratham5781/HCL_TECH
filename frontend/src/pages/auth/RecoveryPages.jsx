import { useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import toast from 'react-hot-toast';
import client from '../../api/client';

function FormPage({reset=false}){
 const [query]=useSearchParams(); const navigate=useNavigate(); const [busy,setBusy]=useState(false);
 const schema=reset?yup.object({password:yup.string().min(10).max(72).required(),confirm:yup.string().oneOf([yup.ref('password')],'Passwords must match').required()}):yup.object({email:yup.string().email().required()});
 const {register,handleSubmit,formState:{errors}}=useForm({resolver:yupResolver(schema)});
 const submit=async(values)=>{setBusy(true);try{if(reset){await client.post('/api/auth/password/reset',{token:query.get('token'),newPassword:values.password});toast.success('Password updated. Sign in with your new password.');navigate('/customer/login');}else{await client.post('/api/auth/password/forgot',values);toast.success('If the account exists, a reset link has been sent.');}}catch(e){toast.error(e.response?.data?.message||'Unable to complete this request.');}finally{setBusy(false);}};
 return <main className="grid min-h-screen place-items-center bg-slate-50 p-5"><section className="card w-full max-w-md"><p className="text-sm font-semibold text-teal-700">UtilityCare account</p><h1 className="mt-2 text-2xl font-bold">{reset?'Choose a new password':'Reset your password'}</h1><p className="mt-2 text-sm text-slate-500">{reset?'Set a new password for your account.':'Enter your account email and we will send a secure reset link.'}</p><form onSubmit={handleSubmit(submit)} className="mt-6 space-y-4">{reset?<><label className="block text-sm font-medium">New password<input className="field mt-2" type="password" autoComplete="new-password" {...register('password')}/>{errors.password&&<small className="text-rose-600">{errors.password.message}</small>}</label><label className="block text-sm font-medium">Confirm password<input className="field mt-2" type="password" autoComplete="new-password" {...register('confirm')}/>{errors.confirm&&<small className="text-rose-600">{errors.confirm.message}</small>}</label></>:<label className="block text-sm font-medium">Email address<input className="field mt-2" type="email" {...register('email')}/>{errors.email&&<small className="text-rose-600">{errors.email.message}</small>}</label>}<button disabled={busy} className="w-full rounded-lg bg-teal-700 px-4 py-3 font-semibold text-white disabled:opacity-60">{busy?'Sending…':reset?'Update password':'Send reset link'}</button></form><Link className="mt-5 block text-center text-sm text-teal-700" to="/customer/login">Return to sign in</Link></section></main>;
}
export function ForgotPasswordPage(){return <FormPage/>}
export function ResetPasswordPage(){return <FormPage reset/>}

