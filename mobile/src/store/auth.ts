import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

import { resetQueryClient } from '../features/queryClient';
import type { AuthResult, User } from '../api/types';

const ACCESS_KEY = 'closette_access';
const REFRESH_KEY = 'closette_refresh';
const USER_KEY = 'closette_user';

let storageWrites = Promise.resolve();
function persist(write: () => Promise<void>): Promise<void> {
  const next = storageWrites.catch(() => undefined).then(write);
  storageWrites = next;
  return next;
}
let revokeSession: (refresh: string) => Promise<void> = async () => undefined;
export function setSessionRevoker(revoke: typeof revokeSession) { revokeSession = revoke; }

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

type AuthState = {
  status: AuthStatus;
  sessionEpoch: number;
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  /** When true, the app opens the verify-email screen once (set right after register). */
  promptVerify: boolean;
  hydrate: () => Promise<void>;
  setSession: (result: AuthResult) => Promise<void>;
  updateTokens: (accessToken: string, refreshToken: string, expectedEpoch: number) => Promise<void>;
  setUser: (user: User) => void;
  setPromptVerify: (value: boolean) => void;
  signOut: () => Promise<void>;
};

export const useAuth = create<AuthState>((set, get) => ({
  status: 'loading',
  sessionEpoch: 0,
  user: null,
  accessToken: null,
  refreshToken: null,
  promptVerify: false,

  hydrate: async () => {
    const epoch = get().sessionEpoch;
    try {
      await storageWrites.catch(() => undefined);
      const [accessToken, refreshToken, rawUser] = await Promise.all([
        SecureStore.getItemAsync(ACCESS_KEY),
        SecureStore.getItemAsync(REFRESH_KEY),
        SecureStore.getItemAsync(USER_KEY),
      ]);
      if (epoch !== get().sessionEpoch) return;
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
      if (epoch === get().sessionEpoch) set({ status: 'unauthenticated' });
    }
  },

  setSession: async (result) => {
    const epoch = get().sessionEpoch + 1;
    resetQueryClient();
    set({ sessionEpoch: epoch, status: 'loading', user: null, accessToken: null, refreshToken: null, promptVerify: false });
    try {
      await persist(async () => {
        if (epoch !== get().sessionEpoch) return;
        await Promise.all([
          SecureStore.setItemAsync(ACCESS_KEY, result.accessToken),
          SecureStore.setItemAsync(REFRESH_KEY, result.refreshToken),
          SecureStore.setItemAsync(USER_KEY, JSON.stringify(result.user)),
        ]);
      });
      if (epoch !== get().sessionEpoch) return;
      set({ status: 'authenticated', accessToken: result.accessToken,
        refreshToken: result.refreshToken, user: result.user });
    } catch (error) {
      if (epoch === get().sessionEpoch) await get().signOut();
      throw error;
    }
  },

  updateTokens: async (accessToken, refreshToken, expectedEpoch) => {
    if (get().sessionEpoch !== expectedEpoch) return;
    await persist(async () => {
      if (get().sessionEpoch !== expectedEpoch) return;
      await Promise.all([
        SecureStore.setItemAsync(ACCESS_KEY, accessToken),
        SecureStore.setItemAsync(REFRESH_KEY, refreshToken),
      ]);
    });
    if (get().sessionEpoch === expectedEpoch) set({ accessToken, refreshToken });
  },

  setUser: (user) => {
    if (get().user?.id !== user.id) return;
    const epoch = get().sessionEpoch;
    set({ user });
    void persist(async () => {
      if (get().sessionEpoch === epoch) await SecureStore.setItemAsync(USER_KEY, JSON.stringify(user));
    }).catch(() => undefined);
  },

  setPromptVerify: (value) => set({ promptVerify: value }),

  signOut: async () => {
    const refresh = get().refreshToken;
    resetQueryClient();
    set({ sessionEpoch: get().sessionEpoch + 1, status: 'unauthenticated', user: null,
      accessToken: null, refreshToken: null, promptVerify: false });
    // Attempt server revocation even if the device's keychain fails to delete.
    const revocation = refresh ? revokeSession(refresh).catch(() => undefined) : Promise.resolve();
    try {
      await persist(async () => {
        await Promise.all([
          SecureStore.deleteItemAsync(ACCESS_KEY), SecureStore.deleteItemAsync(REFRESH_KEY),
          SecureStore.deleteItemAsync(USER_KEY),
        ]);
      });
    } finally {
      await revocation;
    }
  },
}));
