import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  const session = localStorage.getItem('boomerang_session');
  if (session) {
    const { sessionId } = JSON.parse(session);
    config.headers['X-Boomerang-Session-Id'] = sessionId;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('boomerang_session');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default apiClient;
