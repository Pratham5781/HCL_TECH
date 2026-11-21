import client from './client';
export const customerApi={
 dashboard:()=>client.get('/api/customer/dashboard'),bills:(params)=>client.get('/api/customer/bills',{params}),bill:(id)=>client.get(`/api/customer/bills/${id}`),billPdf:(id)=>client.get(`/api/customer/bills/${id}/pdf`,{responseType:'blob'}),
 readings:(params)=>client.get('/api/customer/readings',{params}),payments:(params)=>client.get('/api/customer/payments',{params}),pay:(id,body)=>client.post(`/api/customer/bills/${id}/payments`,body),receipt:(id)=>client.get(`/api/customer/payments/${id}/receipt.pdf`,{responseType:'blob'}),
 services:()=>client.get('/api/customer/services'),requests:(params)=>client.get('/api/customer/connection-requests',{params}),createRequest:(body)=>client.post('/api/customer/connection-requests',body,{headers:{'Content-Type':'multipart/form-data'}}),requestDocument:(id)=>client.get(`/api/customer/connection-requests/${id}/document`,{responseType:'blob'}),updateProfile:(body)=>client.put('/api/customer/profile',body)
};
