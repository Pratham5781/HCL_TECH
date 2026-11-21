import client from './client';
export const notificationApi={list:(params)=>client.get('/api/notifications',{params}),read:(id)=>client.patch(`/api/notifications/${id}/read`),readAll:()=>client.post('/api/notifications/read-all')};
