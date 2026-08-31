import { api, unwrap } from './client';
import type {
  AnalyzeResponse,
  AuthResult,
  BeautyAnalyzeResponse,
  BeautyCategory,
  BeautyItem,
  BeautyProductCandidate,
  CreateBeautyPayload,
  UpdateBeautyPayload,
  ClothingCategory,
  GeneratedLook,
  Outfit,
  ShouldIBuyResult,
  StylePreference,
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
  async deleteAccount(): Promise<void> {
    const { data } = await api.delete<Envelope<null>>('/api/users/me');
    unwrap(data);
  },
  async getStylePreferences(): Promise<StylePreference> {
    const { data } = await api.get<Envelope<StylePreference>>('/api/users/me/style-preferences');
    return unwrap(data);
  },
  async updateStylePreferences(payload: Partial<StylePreference>): Promise<StylePreference> {
    const { data } = await api.put<Envelope<StylePreference>>('/api/users/me/style-preferences', payload);
    return unwrap(data);
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
export type BeautyQuery = {
  category?: BeautyCategory;
  favorite?: boolean;
};

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
  async list(query: BeautyQuery = {}): Promise<BeautyItem[]> {
    const { data } = await api.get<Envelope<BeautyItem[]>>('/api/beauty/items', { params: query });
    return unwrap(data);
  },
  async toggleFavorite(id: string): Promise<BeautyItem> {
    const { data } = await api.patch<Envelope<BeautyItem>>(`/api/beauty/items/${id}/favorite`);
    return unwrap(data);
  },
  async update(id: string, payload: UpdateBeautyPayload): Promise<BeautyItem> {
    const { data } = await api.put<Envelope<BeautyItem>>(`/api/beauty/items/${id}`, payload);
    return unwrap(data);
  },
  async remove(id: string): Promise<void> {
    await api.delete(`/api/beauty/items/${id}`);
  },
  async scanIngredients(fileUri: string, mimeType: string, name: string): Promise<string[]> {
    const form = new FormData();
    form.append('file', { uri: fileUri, name, type: mimeType } as unknown as Blob);
    const { data } = await api.post<Envelope<string[]>>('/api/beauty/ingredients/scan', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return unwrap(data);
  },
  async lookup(barcode: string): Promise<BeautyProductCandidate | null> {
    const { data } = await api.get<Envelope<BeautyProductCandidate | null>>('/api/beauty/lookup', {
      params: { barcode },
    });
    return unwrap(data);
  },
  async search(q: string): Promise<BeautyProductCandidate[]> {
    const { data } = await api.get<Envelope<BeautyProductCandidate[]>>('/api/beauty/search', {
      params: { q },
    });
    return unwrap(data);
  },
  async explainIngredient(name: string): Promise<{ name: string; explanation: string }> {
    const { data } = await api.get<Envelope<{ name: string; explanation: string }>>(
      '/api/beauty/ingredients/explain',
      { params: { name } },
    );
    return unwrap(data);
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
  async save(payload: {
    title: string;
    occasion?: string;
    rationale?: string;
    itemIds: string[];
  }): Promise<Outfit> {
    const { data } = await api.post<Envelope<Outfit>>('/api/outfits', { ...payload, status: 'SAVED' });
    return unwrap(data);
  },
  async remove(id: string): Promise<void> {
    const { data } = await api.delete<Envelope<null>>(`/api/outfits/${id}`);
    unwrap(data);
  },
};

// ---- Should I Buy ----
export const recommendationApi = {
  async describe(description: string): Promise<ShouldIBuyResult> {
    const { data } = await api.post<Envelope<ShouldIBuyResult>>('/api/recommendations/should-i-buy', { description });
    return unwrap(data);
  },
  async fromPhoto(fileUri: string, mimeType: string, name: string): Promise<ShouldIBuyResult> {
    const form = new FormData();
    form.append('file', { uri: fileUri, name, type: mimeType } as unknown as Blob);
    const { data } = await api.post<Envelope<ShouldIBuyResult>>('/api/recommendations/should-i-buy/photo', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return unwrap(data);
  },
};
