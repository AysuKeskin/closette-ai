import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

import type { AuthResult, User } from '../api/types';

const ACCESS_KEY = 'closette_access';
const REFRESH_KEY = 'closette_refresh';
const USER_KEY = 'closette_user';

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

type AuthState = {
  status: AuthStatus;
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  /** When true, the app opens the verify-email screen once (set right after register). */
  promptVerify: boolean;
  hydrate: () => Promise<void>;
  setSession: (result: AuthResult) => Promise<void>;
  updateTokens: (accessToken: string, refreshToken: string) => Promise<void>;
  setUser: (user: User) => void;
  setPromptVerify: (value: boolean) => void;
  signOut: () => Promise<void>;
};

export const useAuth = create<AuthState>((set, get) => ({
  status: 'loading',
  user: null,
  accessToken: null,
  refreshToken: null,
  promptVerify: false,

  hydrate: async () => {
    try {
      const [accessToken, refreshToken, rawUser] = await Promise.all([
        SecureStore.getItemAsync(ACCESS_KEY),
        SecureStore.getItemAsync(REFRESH_KEY),
        SecureStore.getItemAsync(USER_KEY),
      ]);
      if (accessToken && refreshToken && rawUser) {
        set({
          status: 'authenticated',
          accessToken,
          refreshToken,
          user: JSON.parse(rawUser) as User,
        });
      } else {
        set({ status: 'unauthenticated' });
      }
    } catch {
      set({ status: 'unauthenticated' });
    }
  },

  setSession: async (result) => {
    await Promise.all([
      SecureStore.setItemAsync(ACCESS_KEY, result.accessToken),
      SecureStore.setItemAsync(REFRESH_KEY, result.refreshToken),
      SecureStore.setItemAsync(USER_KEY, JSON.stringify(result.user)),
    ]);
    set({
      status: 'authenticated',
      accessToken: result.accessToken,
      refreshToken: result.refreshToken,
      user: result.user,
    });
  },

  updateTokens: async (accessToken, refreshToken) => {
    await Promise.all([
      SecureStore.setItemAsync(ACCESS_KEY, accessToken),
      SecureStore.setItemAsync(REFRESH_KEY, refreshToken),
    ]);
    set({ accessToken, refreshToken });
  },

  setUser: (user) => {
    set({ user });
    SecureStore.setItemAsync(USER_KEY, JSON.stringify(user)).catch(() => undefined);
  },

  setPromptVerify: (value) => set({ promptVerify: value }),

  signOut: async () => {
    await Promise.all([
      SecureStore.deleteItemAsync(ACCESS_KEY),
      SecureStore.deleteItemAsync(REFRESH_KEY),
      SecureStore.deleteItemAsync(USER_KEY),
    ]);
    set({ status: 'unauthenticated', user: null, accessToken: null, refreshToken: null });
  },
}));
