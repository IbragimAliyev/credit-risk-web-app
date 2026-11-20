import axios from 'axios';

export const TOKEN_KEY = 'crs_token';
export const USER_KEY = 'crs_user';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Listeners notified when the API rejects our token (expired / invalid).
const unauthorizedListeners = new Set();
export function onUnauthorized(listener) {
  unauthorizedListeners.add(listener);
  return () => unauthorizedListeners.delete(listener);
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const isAuthCall = error.config?.url?.startsWith('/auth/');
    if (error.response?.status === 401 && !isAuthCall) {
      unauthorizedListeners.forEach((fn) => fn());
    }
    return Promise.reject(error);
  },
);

/** Extracts a user-facing message (and per-field errors) from an axios error. */
export function parseApiError(error) {
  if (!error.response) {
    return { message: 'Cannot reach the server. Is the backend running?', fieldErrors: {} };
  }
  const data = error.response.data || {};
  return {
    message: data.message || `Request failed (${error.response.status})`,
    fieldErrors: data.errors || {},
  };
}

export const authApi = {
  login: (email, password) => api.post('/auth/login', { email, password }).then((r) => r.data),
  register: (name, email, password) =>
    api.post('/auth/register', { name, email, password }).then((r) => r.data),
};

export const applicationsApi = {
  list: () => api.get('/api/applications').then((r) => r.data),
  get: (id) => api.get(`/api/applications/${id}`).then((r) => r.data),
  create: (payload) => api.post('/api/applications', payload).then((r) => r.data),
};

export const modelApi = {
  info: () => api.get('/api/model').then((r) => r.data),
};

export default api;
