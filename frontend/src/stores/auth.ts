import { create } from 'zustand';
import type { LoginResponse } from '../types';

export type AuthSession = LoginResponse & {
  loginAt: string;
};

type AuthState = {
  session: AuthSession | null;
  isAuthenticated: boolean;
  setSession: (session: LoginResponse) => void;
  clearSession: () => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  session: null,
  isAuthenticated: false,
  setSession: (session) => {
    const nextSession: AuthSession = {
      ...session,
      loginAt: new Date().toISOString(),
    };
    set({
      session: nextSession,
      isAuthenticated: true,
    });
  },
  clearSession: () => {
    set({
      session: null,
      isAuthenticated: false,
    });
  },
}));
