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
  logout: () => void;
  setAuth: (user: User, accessToken: string, refreshToken: string) => void;
  setUser: (user: User) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      
      login: async (email, password) => {
        const response = await axios.post(`${API_URL}/login`, { email, password });
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
          user, 
          accessToken: data.accessToken, 
          refreshToken: data.refreshToken 
        });
      },

      register: async (name, email, password) => {
        const response = await axios.post(`${API_URL}/register`, { 
          fullName: name, // Backend expects fullName
          email, 
          password 
        });
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
          user, 
          accessToken: data.accessToken, 
          refreshToken: data.refreshToken 
        });
      },

      logout: () => set({ user: null, accessToken: null, refreshToken: null }),
      
      setAuth: (user, accessToken, refreshToken) =>
        set({ user, accessToken, refreshToken }),
        
      setUser: (user) => set({ user }),
    }),
    {
      name: 'auth-storage',
    }
  )
);
