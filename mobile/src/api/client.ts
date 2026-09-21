import axios, { AxiosError, AxiosHeaders } from 'axios';
import Constants from 'expo-constants';
import { Platform } from 'react-native';

import { translate } from '../i18n';
import { useAuth, setSessionRevoker } from '../store/auth';
import { currentLanguage } from '../store/locale';
import { ensureAiConsent, needsAiConsent } from './consent';
import type { ApiEnvelope, AuthResult } from './types';

/**
 * Resolves the backend base URL. Priority:
 * 1. EXPO_PUBLIC_API_BASE_URL env var
 * 2. app.json extra.apiBaseUrl
 * 3. Platform default (Android emulator needs 10.0.2.2 to reach the host)
 */
function resolveBaseUrl(): string {
  const fromEnv = process.env.EXPO_PUBLIC_API_BASE_URL;
  if (fromEnv) return fromEnv.replace(/\/$/, '');
  const fromExtra = (Constants.expoConfig?.extra as { apiBaseUrl?: string } | undefined)?.apiBaseUrl;
  if (fromExtra) return fromExtra.replace(/\/$/, '');
  if (!__DEV__) throw new Error('Missing production API URL');
  return Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080';
}

export const API_BASE_URL = resolveBaseUrl();

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
});

// Attach the access token and the user's language to every request. The backend
// answers in that language: its error messages and the AI's prose come back
// ready to display, so the app never translates server text itself.
declare module 'axios' {
  interface InternalAxiosRequestConfig {
    _sessionEpoch?: number;
    _retry?: boolean;
  }
}

const bare = axios.create({ baseURL: API_BASE_URL, timeout: 30000 });
setSessionRevoker(async (refreshToken) => { await bare.post('/api/auth/logout', { refreshToken }); });

api.interceptors.request.use(async (config) => {
  const epoch = useAuth.getState().sessionEpoch;
  if (config._sessionEpoch !== undefined && config._sessionEpoch !== epoch) throw new axios.CanceledError();
  config._sessionEpoch = epoch;
  const isAuth = config.url?.startsWith('/api/auth/');
  if (!isAuth && needsAiConsent(config)) await ensureAiConsent(bare, epoch, async (version) => {
    await api.put('/api/users/me/ai-consent', { version, accepted: true });
  });
  if (useAuth.getState().sessionEpoch !== epoch) throw new axios.CanceledError();
  const headers = AxiosHeaders.from(config.headers);
  const token = useAuth.getState().accessToken;
  if (!isAuth && token) headers.set('Authorization', `Bearer ${token}`);
  headers.set('Accept-Language', currentLanguage());
  config.headers = headers;
  return config;
});

let refreshing: { epoch: number; promise: Promise<string | null> } | null = null;

async function refreshAccessToken(epoch: number): Promise<string | null> {
  const { refreshToken } = useAuth.getState();
  if (!refreshToken || useAuth.getState().sessionEpoch !== epoch) return null;
  try {
    const resp = await bare.post<ApiEnvelope<AuthResult>>('/api/auth/refresh', { refreshToken });
    const data = resp.data.data;
    if (!data || epoch !== useAuth.getState().sessionEpoch) return null;
    await useAuth.getState().updateTokens(data.accessToken, data.refreshToken, epoch);
    return epoch === useAuth.getState().sessionEpoch ? data.accessToken : null;
  } catch { return null; }
}

api.interceptors.response.use(
  (response) => {
    if (response.config._sessionEpoch !== useAuth.getState().sessionEpoch) throw new axios.CanceledError();
    return response;
  },
  async (error: AxiosError) => {
    const original = error.config;
    const epoch = original?._sessionEpoch;
    if (epoch !== undefined && epoch !== useAuth.getState().sessionEpoch) throw new axios.CanceledError();
    if (error.response?.status === 401 && original && !original._retry
        && !original.url?.startsWith('/api/auth/') && epoch !== undefined) {
      original._retry = true;
      if (!refreshing || refreshing.epoch !== epoch) {
        const current = { epoch, promise: refreshAccessToken(epoch) };
        refreshing = current;
        void current.promise.finally(() => { if (refreshing === current) refreshing = null; });
      }
      const token = await refreshing.promise;
      if (epoch !== useAuth.getState().sessionEpoch) throw new axios.CanceledError();
      if (token) return api.request(original);
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
    throw new ApiError(
      envelope.error?.code ?? 'INTERNAL',
      envelope.error?.message ?? translate(currentLanguage(), 'errors.generic'),
    );
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
  if (axios.isCancel(err)) return new ApiError('CANCELED', err.message || translate(currentLanguage(), 'consent.declined'));
  if (axios.isAxiosError(err)) {
    const envelope = err.response?.data as ApiEnvelope<unknown> | undefined;
    if (envelope?.error) return new ApiError(envelope.error.code, envelope.error.message);
    const language = currentLanguage();
    if (err.code === 'ECONNABORTED') {
      return new ApiError('TIMEOUT', translate(language, 'errors.timeout'));
    }
    return new ApiError('NETWORK', translate(language, 'errors.network'));
  }
  return new ApiError('INTERNAL', translate(currentLanguage(), 'errors.generic'));
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

// Turkish's dotted capital: "içerik" must become "İçerik", not "Içerik".
function capitalize(s: string): string {
  const locale = currentLanguage() === 'tr' ? 'tr-TR' : 'en-US';
  return s.charAt(0).toLocaleUpperCase(locale) + s.slice(1);
}
