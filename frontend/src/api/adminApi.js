import client from './client';
export const adminApi = {
  dashboard: () => client.get('/api/admin/dashboard'),
  reports: () => client.get('/api/admin/reports'),
  accounts: (params) => client.get('/api/admin/accounts', { params }),
  createAccount: (body) => client.post('/api/admin/accounts', body),
  updateAccount: (id, body) => client.put(`/api/admin/accounts/${id}`, body),
  setAccountStatus: (id, status) => client.patch(`/api/admin/accounts/${id}/status`, { status }),
  deleteAccount: (id) => client.delete(`/api/admin/accounts/${id}`),
  resetPassword: (id, newPassword) => client.post(`/api/admin/accounts/${id}/password`, { newPassword }),
  registrations: (params) => client.get('/api/admin/registrations', { params }),
  decideRegistration: (id, decision) => client.patch(`/api/admin/registrations/${id}`, { decision }),
  connectionRequests: (params) => client.get('/api/admin/connection-requests', { params }),
  decideConnection: (id, decision) => client.patch(`/api/admin/connection-requests/${id}`, { decision }),
  list: (resource, params) => client.get(`/api/admin/data/${resource}`, { params }),
  exportUrl: (resource, format, params = {}) => {
    const query = new URLSearchParams({ ...params, format }).toString();
    return `/api/admin/data/${resource}/export?${query}`;
  },
  services: () => client.get('/api/admin/services'),
  createService: (body) => client.post('/api/admin/services', body),
  updateService: (id, body) => client.put(`/api/admin/services/${id}`, body),
  connectionTypes: () => client.get('/api/admin/connection-types'),
  createType: (body) => client.post('/api/admin/connection-types', body),
  updateType: (id, body) => client.put(`/api/admin/connection-types/${id}`, body),
  deleteType: (id) => client.delete(`/api/admin/connection-types/${id}`),
  zones: () => client.get('/api/admin/zones'),
  createZone: (body) => client.post('/api/admin/zones', body),
  updateZone: (id, body) => client.put(`/api/admin/zones/${id}`, body),
  deleteZone: (id) => client.delete(`/api/admin/zones/${id}`),
  tariffs: () => client.get('/api/admin/tariffs'),
  createTariff: (body) => client.post('/api/admin/tariffs', body),
  updateTariff: (id, body) => client.put(`/api/admin/tariffs/${id}`, body),
  deleteTariff: (id) => client.delete(`/api/admin/tariffs/${id}`),
  assignments: () => client.get('/api/admin/assignments'),
  assignZones: (id, ids) => client.put(`/api/admin/assignments/operators/${id}/zones`, { ids }),
  assignCustomers: (id, ids) => client.put(`/api/admin/assignments/operators/${id}/customers`, { ids })
};

