import axios from 'axios';

// Use relative URL in development to go through Vite proxy, full URL in production
import { API_BASE_URL } from './config';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

import { useAuthStore } from '@/store/authStore';

// Request interceptor to add auth token
api.interceptors.request.use(
  (config) => {
    const token = useAuthStore.getState().accessToken;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor to handle token refresh
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const refreshToken = useAuthStore.getState().refreshToken;
        if (refreshToken) {
          const response = await axios.post(`${API_BASE_URL}/api/v1/auth/refresh`, {
            refreshToken,
          });

          const { accessToken, refreshToken: newRefreshToken, user: refreshedUser } = response.data.data;
          const user = useAuthStore.getState().user;
          if (user) {
             useAuthStore.getState().setAuth({ ...user, role: refreshedUser.role, credits: refreshedUser.credits }, accessToken, newRefreshToken);
          }

          originalRequest.headers.Authorization = `Bearer ${accessToken}`;
          return api(originalRequest);
        }
      } catch (refreshError) {
        useAuthStore.getState().logout();
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    // Enhance error with additional context
    if (error.response) {
      error.userMessage = error.response.data?.message || getErrorMessage(error.response.status);
    } else if (error.request) {
      error.userMessage = 'Network error. Please check your connection.';
    } else {
      error.userMessage = 'An unexpected error occurred.';
    }

    return Promise.reject(error);
  }
);

function getErrorMessage(status: number): string {
  switch (status) {
    case 400:
      return 'Invalid request. Please check your input.';
    case 401:
      return 'You need to log in to perform this action.';
    case 403:
      return 'You do not have permission to perform this action.';
    case 404:
      return 'The requested resource was not found.';
    case 500:
      return 'Server error. Please try again later.';
    case 503:
      return 'Service unavailable. Please try again later.';
    default:
      return 'An error occurred. Please try again.';
  }
}

export default api;
