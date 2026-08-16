import axios, { AxiosError, AxiosHeaders } from 'axios';
import Constants from 'expo-constants';
import { Platform } from 'react-native';

import { useAuth } from '../store/auth';
import type { ApiEnvelope, AuthResult } from './types';

/**
 * Resolves the backend base URL. Priority:
 * 1. EXPO_PUBLIC_API_BASE_URL env var
 * 2. app.json extra.apiBaseUrl
 * 3. Platform default (Android emulator needs 10.0.2.2 to reach the host)
 */
function resolveBaseUrl(): string {
  const fromEnv = process.env.EXPO_PUBLIC_API_BASE_URL;
  if (fromEnv) return fromEnv;
  const fromExtra = (Constants.expoConfig?.extra as { apiBaseUrl?: string } | undefined)?.apiBaseUrl;
  if (fromExtra && Platform.OS !== 'android') return fromExtra;
  return Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080';
}

export const API_BASE_URL = resolveBaseUrl();

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
});

// Attach the access token to every request.
api.interceptors.request.use((config) => {
  const token = useAuth.getState().accessToken;
  if (token) {
    const headers = AxiosHeaders.from(config.headers);
    headers.set('Authorization', `Bearer ${token}`);
    config.headers = headers;
  }
  return config;
});

// A bare client (no interceptors) for the refresh call, to avoid recursion.
const bare = axios.create({ baseURL: API_BASE_URL, timeout: 30000 });

let refreshing: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const { refreshToken } = useAuth.getState();
  if (!refreshToken) return null;
  try {
    const resp = await bare.post<ApiEnvelope<AuthResult>>('/api/auth/refresh', { refreshToken });
    const data = resp.data.data;
    if (!data) return null;
    await useAuth.getState().updateTokens(data.accessToken, data.refreshToken);
    return data.accessToken;
  } catch {
    return null;
  }
}

// On 401, try a single refresh + retry; otherwise sign out.
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (typeof error.config & { _retry?: boolean }) | undefined;
    const status = error.response?.status;
    if (status === 401 && original && !original._retry) {
      original._retry = true;
      if (!refreshing) {
        refreshing = refreshAccessToken().finally(() => {
          refreshing = null;
        });
      }
      const newToken = await refreshing;
      if (newToken) {
        const headers = AxiosHeaders.from(original.headers);
        headers.set('Authorization', `Bearer ${newToken}`);
        original.headers = headers;
        return api.request(original);
      }
      await useAuth.getState().signOut();
    }
    return Promise.reject(error);
  },
);

/**
 * Unwraps the ApiResponse envelope, throwing a readable Error on failure.
 * `data` may legitimately be null on success (void endpoints like
 * forgot-password / reset-password / logout), so we key off `success` only.
 */
export function unwrap<T>(envelope: ApiEnvelope<T>): T {
  if (!envelope.success) {
    throw new ApiError(envelope.error?.code ?? 'INTERNAL', envelope.error?.message ?? 'Request failed');
  }
  return envelope.data as T;
}

export class ApiError extends Error {
  code: string;
  constructor(code: string, message: string) {
    super(message);
    this.code = code;
    this.name = 'ApiError';
  }
}

/** Normalizes any thrown value (axios/envelope) into an ApiError. */
export function toApiError(err: unknown): ApiError {
  if (err instanceof ApiError) return err;
  if (axios.isAxiosError(err)) {
    const envelope = err.response?.data as ApiEnvelope<unknown> | undefined;
    if (envelope?.error) return new ApiError(envelope.error.code, envelope.error.message);
    if (err.code === 'ECONNABORTED') return new ApiError('TIMEOUT', 'The request timed out. Please try again.');
    return new ApiError('NETWORK', 'Could not reach the server. Check your connection.');
  }
  return new ApiError('INTERNAL', 'Something went wrong.');
}

/**
 * Backend validation errors arrive as one string, e.g.
 * "email: must not be blank; password: must not be blank". Split it back into a
 * per-field map so each message can render under its own input. Anything without
 * a recognized `field:` prefix is returned under `form`.
 */
export function toFieldErrors(message: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const part of message.split(';')) {
    const trimmed = part.trim();
    if (!trimmed) continue;
    const match = trimmed.match(/^(\w+):\s*(.+)$/);
    if (match) {
      out[match[1]] = capitalize(match[2]);
    } else {
      out.form = out.form ? `${out.form} ${trimmed}` : trimmed;
    }
  }
  return out;
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
