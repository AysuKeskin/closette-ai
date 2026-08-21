import { api, unwrap } from './client';
import type {
  AnalyzeResponse,
  AuthResult,
  BeautyAnalyzeResponse,
  BeautyItem,
  CreateBeautyPayload,
  ClothingCategory,
  GeneratedLook,
  Outfit,
  ShouldIBuyResult,
  User,
  WardrobeItem,
} from './types';

type Envelope<T> = { success: boolean; data: T | null; error: { code: string; message: string } | null };

// ---- Auth ----
export const authApi = {
  async register(
    email: string,
    username: string,
    password: string,
    displayName?: string,
  ): Promise<AuthResult> {
    const { data } = await api.post<Envelope<AuthResult>>('/api/auth/register', {
      email,
      username,
      password,
      displayName,
    });
    return unwrap(data);
  },
  async login(email: string, password: string): Promise<AuthResult> {
    const { data } = await api.post<Envelope<AuthResult>>('/api/auth/login', { email, password });
    return unwrap(data);
  },
  async forgotPassword(email: string): Promise<void> {
    const { data } = await api.post<Envelope<null>>('/api/auth/forgot-password', { email });
    unwrap(data);
  },
  async resetPassword(email: string, code: string, newPassword: string): Promise<void> {
    const { data } = await api.post<Envelope<null>>('/api/auth/reset-password', {
      email,
      code,
      newPassword,
    });
    unwrap(data);
  },
};

// ---- User ----
export const userApi = {
  async me(): Promise<User> {
    const { data } = await api.get<Envelope<User>>('/api/users/me');
    return unwrap(data);
  },
  async verifyEmail(code: string): Promise<User> {
    const { data } = await api.post<Envelope<User>>('/api/users/me/email/verify', { code });
    return unwrap(data);
  },
  async resendVerification(): Promise<void> {
    const { data } = await api.post<Envelope<null>>('/api/users/me/email/resend', {});
    unwrap(data);
  },
};

// ---- Wardrobe ----
export type WardrobeQuery = {
  category?: ClothingCategory;
  color?: string;
  season?: string;
  brand?: string;
  favorite?: boolean;
  q?: string;
};

export type CreateItemPayload = {
  name: string;
  category: ClothingCategory;
  subcategory?: string;
  colors?: string[];
  pattern?: string;
  styles?: string[];
  seasons?: string[];
  brand?: string;
  size?: string;
  imageKey?: string;
  favorite?: boolean;
};

export const wardrobeApi = {
  async analyze(fileUri: string, mimeType: string, name: string): Promise<AnalyzeResponse> {
    const form = new FormData();
    // React Native FormData file shape.
    form.append('file', { uri: fileUri, name, type: mimeType } as unknown as Blob);
    const { data } = await api.post<Envelope<AnalyzeResponse>>('/api/wardrobe/analyze', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return unwrap(data);
  },
  async create(payload: CreateItemPayload): Promise<WardrobeItem> {
    const { data } = await api.post<Envelope<WardrobeItem>>('/api/wardrobe/items', payload);
    return unwrap(data);
  },
  async list(query: WardrobeQuery = {}): Promise<WardrobeItem[]> {
    const { data } = await api.get<Envelope<WardrobeItem[]>>('/api/wardrobe/items', { params: query });
    return unwrap(data);
  },
  async recent(limit = 8): Promise<WardrobeItem[]> {
    const { data } = await api.get<Envelope<WardrobeItem[]>>('/api/wardrobe/items/recent', {
      params: { limit },
    });
    return unwrap(data);
  },
  async similar(id: string, limit = 6): Promise<WardrobeItem[]> {
    const { data } = await api.get<Envelope<WardrobeItem[]>>(`/api/wardrobe/items/${id}/similar`, {
      params: { limit },
    });
    return unwrap(data);
  },
  async toggleFavorite(id: string): Promise<WardrobeItem> {
    const { data } = await api.patch<Envelope<WardrobeItem>>(`/api/wardrobe/items/${id}/favorite`);
    return unwrap(data);
  },
  async remove(id: string): Promise<void> {
    await api.delete(`/api/wardrobe/items/${id}`);
  },
};

// ---- Beauty ----
export const beautyApi = {
  async analyze(fileUri: string, mimeType: string, name: string): Promise<BeautyAnalyzeResponse> {
    const form = new FormData();
    form.append('file', { uri: fileUri, name, type: mimeType } as unknown as Blob);
    const { data } = await api.post<Envelope<BeautyAnalyzeResponse>>('/api/beauty/analyze', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return unwrap(data);
  },
  async create(payload: CreateBeautyPayload): Promise<BeautyItem> {
    const { data } = await api.post<Envelope<BeautyItem>>('/api/beauty/items', payload);
    return unwrap(data);
  },
  async list(category?: string): Promise<BeautyItem[]> {
    const { data } = await api.get<Envelope<BeautyItem[]>>('/api/beauty/items', {
      params: category ? { category } : {},
    });
    return unwrap(data);
  },
  async remove(id: string): Promise<void> {
    await api.delete(`/api/beauty/items/${id}`);
  },
};

// ---- Outfits / Get Ready ----
export const outfitApi = {
  async generate(prompt: string, occasion?: string): Promise<GeneratedLook> {
    const { data } = await api.post<Envelope<GeneratedLook>>('/api/outfits/generate', { prompt, occasion });
    return unwrap(data);
  },
  async saved(): Promise<Outfit[]> {
    const { data } = await api.get<Envelope<Outfit[]>>('/api/outfits');
    return unwrap(data);
  },
  async save(payload: { title: string; occasion?: string; itemIds: string[] }): Promise<Outfit> {
    const { data } = await api.post<Envelope<Outfit>>('/api/outfits', { ...payload, status: 'SAVED' });
    return unwrap(data);
  },
};

// ---- Should I Buy ----
export const recommendationApi = {
  async shouldIBuy(input: {
    category?: ClothingCategory;
    colors?: string[];
    styles?: string[];
  }): Promise<ShouldIBuyResult> {
    const { data } = await api.post<Envelope<ShouldIBuyResult>>('/api/recommendations/should-i-buy', input);
    return unwrap(data);
  },
};
