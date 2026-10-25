import { withAuthLock } from '@/lib/authLock';
import { API_BASE_URL } from '@/lib/config';
import axios from 'axios';
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

const API_URL = `${API_BASE_URL}/api/v1/auth`;

interface User {
  id: string;
  email: string;
  name: string;
  role: string;
  onboardingCompleted: boolean;
  workMode?: string;
  targetRole?: string;
  currentOrg?: string;
  targetSalary?: string;
  credits: number;
}

interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  sessionKey: string | null;
  logout: () => Promise<void>;
  clearAuth: () => void;
  setAuth: (user: User, accessToken: string, refreshToken: string) => void;
  setUser: (user: User) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      sessionKey: null,
      user: null,
      accessToken: null,
      refreshToken: null,
      
      login: (email, password) => withAuthLock(async () => {
        const response = await axios.post(`${API_URL}/login`, { email, password }, { timeout: 15000 });
        const data = response.data.data;
        
        // Map backend UserInfo (fullName) to frontend User (name)
        const user: User = {
          id: data.user.id,
          email: data.user.email,
          name: data.user.fullName,
          role: data.user.role,
          onboardingCompleted: data.user.onboardingCompleted,
          workMode: data.user.workMode,
          targetRole: data.user.targetRole,
          currentOrg: data.user.currentOrg,
          targetSalary: data.user.targetSalary,
          credits: data.user.credits ?? 0
        };
        
        set({ 
          sessionKey: crypto.randomUUID(),
          user, 
          accessToken: data.accessToken, 
          refreshToken: data.refreshToken 
        });
      }),

      register: (name, email, password) => withAuthLock(async () => {
        const response = await axios.post(`${API_URL}/register`, { 
          fullName: name, // Backend expects fullName
          email, 
          password 
        }, { timeout: 15000 });
        const data = response.data.data;
        
        const user: User = {
          id: data.user.id,
          email: data.user.email,
          name: data.user.fullName,
          role: data.user.role,
          onboardingCompleted: data.user.onboardingCompleted || false,
          credits: data.user.credits ?? 0
        };
        
        set({ 
          sessionKey: crypto.randomUUID(),
          user, 
          accessToken: data.accessToken, 
          refreshToken: data.refreshToken 
        });
      }),

      logout: async () => {
        const expected = get().sessionKey;
        await withAuthLock(async () => {
          await useAuthStore.persist.rehydrate();
          if (get().sessionKey !== expected) return;
          const refreshToken = get().refreshToken;
          get().clearAuth();
          if (refreshToken) await axios.post(`${API_URL}/logout`, { refreshToken }, { timeout: 15000 });
        });
      },
      clearAuth: () => set({ user: null, accessToken: null, refreshToken: null, sessionKey: null }),
      
      setAuth: (user, accessToken, refreshToken) =>
        set({ user, accessToken, refreshToken }),
        
      setUser: (user) => {
        if (get().sessionKey && get().user?.id === user.id) set({ user });
      },
    }),
    {
      name: 'auth-storage',
      version: 1,
      // Older JWTs have no persisted server session. Require a fresh login after upgrade.
      migrate: () => ({ user: null, accessToken: null, refreshToken: null, sessionKey: null }),
    }
  )
);

// Refresh/logout in another tab must update this tab's UI and outgoing credentials.
window.addEventListener('storage', (event) => {
  if (event.key === 'auth-storage' || event.key === null) void useAuthStore.persist.rehydrate();
});
