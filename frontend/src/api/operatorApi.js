import client from './client';
export const operatorApi={
 billingConnections:(cycle)=>client.get('/api/operator/billing-connections',{params:{cycle}}),
 customers:(params)=>client.get('/api/operator/customers',{params}),
 connections:(customerId)=>client.get(`/api/operator/customers/${customerId}/connections`),
 previous:(connectionId)=>client.get('/api/operator/readings/previous',{params:{connectionId}}),
 readings:(params)=>client.get('/api/operator/readings',{params}),
 readingPhoto:(id)=>client.get(`/api/operator/readings/${id}/photo`,{responseType:'blob'}),
 createReading:(body)=>client.post('/api/operator/readings',body,{headers:{'Content-Type':'multipart/form-data'}}),
 bills:(params)=>client.get('/api/operator/bills',{params}),
 generateBill:(body)=>client.post('/api/operator/bills',body),
 bulkBills:(body)=>client.post('/api/operator/bills/bulk',body),
 regenerateBill:(id,body)=>client.put(`/api/operator/bills/${id}`,body),
 lpgBookings:(params)=>client.get('/api/operator/lpg-bookings',{params}),
 createLpgBooking:(body)=>client.post('/api/operator/lpg-bookings',body),
 updateLpgStatus:(id,status)=>client.patch(`/api/operator/lpg-bookings/${id}/status`,{status})
};

