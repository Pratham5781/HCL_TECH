import axios from 'axios';
import toast from 'react-hot-toast';

const client = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080', headers: { 'Content-Type': 'application/json' } });
let refreshing = null;
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
client.interceptors.response.use((response) => response, async (error) => {
  const original = error.config;
  if (error.response?.status === 401 && !original?._retried && localStorage.getItem('refreshToken') && !original?.url?.includes('/api/auth/')) {
    original._retried = true;
    try {
      refreshing ||= client.post('/api/auth/refresh', { refreshToken: localStorage.getItem('refreshToken') });
      const { data } = await refreshing;
      const pair = data.data;
      localStorage.setItem('accessToken', pair.accessToken);
      localStorage.setItem('refreshToken', pair.refreshToken);
      original.headers.Authorization = `Bearer ${pair.accessToken}`;
      return client(original);
    } catch {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.dispatchEvent(new Event('auth:expired'));
    } finally { refreshing = null; }
  }
  if (error.response?.status === 403) toast.error('Access denied for this account.');
  if (error.response?.status === 401 && !localStorage.getItem('refreshToken')) window.dispatchEvent(new Event('auth:expired'));
  if (error.response?.status === 401 && original?.url?.includes('/api/auth/refresh')) window.dispatchEvent(new Event('auth:expired'));
  else if (!error.config?.skipGlobalError && error.response?.status !== 401) toast.error(error.response?.data?.message || 'The request failed.');
  return Promise.reject(error);
});
export default client;

