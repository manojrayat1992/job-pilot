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

import { withAuthLock } from './authLock';
import { refreshSession } from './sessionCoordinator';

declare module 'axios' {
  interface AxiosRequestConfig {
    expectedSessionKey?: string | null;
  }
  interface InternalAxiosRequestConfig {
    _retry?: boolean;
    _authSessionKey?: string | null;
    _authAccessToken?: string | null;
  }
}

api.interceptors.request.use(async (config) => {
  await useAuthStore.persist.rehydrate();
  const state = useAuthStore.getState();
  if ((config.expectedSessionKey !== undefined && config.expectedSessionKey !== state.sessionKey)
      || (config._retry && config._authSessionKey !== state.sessionKey)) {
    throw new Error('Your login changed. Please try again.');
  }
  config._authSessionKey = state.sessionKey;
  config._authAccessToken = state.accessToken;
  if (state.accessToken) config.headers.Authorization = `Bearer ${state.accessToken}`;
  else delete config.headers.Authorization;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && originalRequest && !originalRequest._retry
        && originalRequest._authSessionKey && originalRequest._authAccessToken) {
      originalRequest._retry = true;
      try {
        await refreshSession(originalRequest._authSessionKey, originalRequest._authAccessToken, {
          lock: (action) => withAuthLock(async () => {
            await useAuthStore.persist.rehydrate();
            return action();
          }),
          read: () => {
            // localStorage rehydration is synchronous; also catches cross-tab changes while awaiting HTTP.
            void useAuthStore.persist.rehydrate();
            return useAuthStore.getState();
          },
          exchange: async (refreshToken) => {
            if (!navigator.locks) throw new Error('Automatic renewal requires HTTPS and Web Locks. Sign in again.');
            const response = await axios.post(`${API_BASE_URL}/api/v1/auth/refresh`, { refreshToken }, { timeout: 15000 });
            const { accessToken, refreshToken: replacement, user } = response.data.data;
            return { ...useAuthStore.getState(), accessToken, refreshToken: replacement,
              user: { ...user, name: user.fullName } };
          },
          write: (session) => {
            if (!session.user) throw new Error('Missing user');
            useAuthStore.getState().setAuth(session.user, session.accessToken!, session.refreshToken!);
          },
          clear: () => useAuthStore.getState().clearAuth(),
        });
        return api(originalRequest);
      } catch (refreshError) {
        if (!useAuthStore.getState().sessionKey) window.location.assign('/login');
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
