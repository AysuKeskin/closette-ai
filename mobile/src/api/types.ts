// Mirrors the backend DTOs (ai.closette.*). Kept intentionally close to the
// server shape so screens can consume responses with no translation layer.

export type ApiEnvelope<T> = {
  success: boolean;
  data: T | null;
  error: { code: string; message: string } | null;
};

export type User = {
  id: string;
  email: string;
  username: string;
  displayName: string | null;
  emailVerified: boolean;
  /** ISO instant the outstanding verification code stops working; null once verified. */
  verificationExpiresAt: string | null;
  createdAt: string;
};

export type AuthResult = {
  accessToken: string;
  refreshToken: string;
  user: User;
};

export type StylePreference = {
  favoriteColors: string[];
  preferredStyles: string[];
  colorSeason: string | null;
  lovedAesthetics: string[];
  dressUp: string | null;
  onboardingCompleted: boolean;
};

export const CLOTHING_CATEGORIES = [
  'TOPS',
  'BOTTOMS',
  'DRESSES',
  'OUTERWEAR',
  'SHOES',
  'BAGS',
  'JEWELRY',
  'ACCESSORIES',
] as const;
export type ClothingCategory = (typeof CLOTHING_CATEGORIES)[number];

export const SEASONS = ['spring', 'summer', 'fall', 'winter'] as const;

export const BEAUTY_CATEGORIES = [
  'SKINCARE',
  'MAKEUP',
  'HAIRCARE',
  'BODYCARE',
  'PERFUME',
  'NAILS',
] as const;
export type BeautyCategory = (typeof BEAUTY_CATEGORIES)[number];

export type ColorDetail = {
  name: string;
  hex: string;
  percentage: number;
};

export type ClothingAnalysis = {
  category: string;
  subcategory: string;
  colors: string[];
  color_details?: ColorDetail[];
  pattern: string;
  styles: string[];
  seasons: string[];
  confidence: number;
};

export type WardrobeItem = {
  id: string;
  name: string;
  category: ClothingCategory;
  subcategory: string | null;
  colors: string[];
  pattern: string | null;
  styles: string[];
  seasons: string[];
  brand: string | null;
  size: string | null;
  imageUrl: string | null;
  favorite: boolean;
  createdAt: string;
};

export type AnalyzeResponse = {
  imageKey: string;
  imageUrl: string | null;
  analysis: ClothingAnalysis;
};

export type BeautyItem = {
  id: string;
  brand: string | null;
  productName: string;
  category: BeautyCategory;
  imageUrl: string | null;
  size: string | null;
  ingredients: string[];
  purchaseDate: string | null;
  openedDate: string | null;
  expiryDate: string | null;
  paoMonths: number | null;
  amountRemaining: number | null;
  favorite: boolean;
};

export type BeautyAnalysis = {
  brand: string;
  productName: string;
  category: string;
  confidence: number;
};

export type BeautyAnalyzeResponse = {
  imageKey: string;
  imageUrl: string | null;
  analysis: BeautyAnalysis;
};

export type BeautyProductCandidate = {
  barcode: string | null;
  productName: string;
  brand: string | null;
  category: BeautyCategory;
  ingredients: string[];
  imageUrl: string | null;
};

export type CreateBeautyPayload = {
  brand?: string;
  productName: string;
  category: BeautyCategory;
  size?: string;
  ingredients?: string[];
  imageKey?: string;
  imageUrl?: string;
  favorite?: boolean;
};

export type UpdateBeautyPayload = {
  brand?: string;
  productName?: string;
  category?: BeautyCategory;
  size?: string;
  ingredients?: string[];
  favorite?: boolean;
  /** A key from a fresh upload; omitted leaves the current photo alone. */
  imageKey?: string;
};

export type GeneratedLook = {
  title: string;
  rationale: string;
  items: WardrobeItem[];
};

export type Outfit = {
  id: string;
  title: string | null;
  occasion: string | null;
  rationale: string | null;
  status: 'SAVED' | 'WORN';
  favorite: boolean;
  items: WardrobeItem[];
  createdAt: string;
};

export type BuyVerdict = 'buy' | 'maybe' | 'skip';

export type ShouldIBuyResult = {
  matchScore: number;
  verdict: BuyVerdict;
  matchingItemCount: number;
  similarItemCount: number;
  similarItems: WardrobeItem[];
  explanation: string;
  detectedLabel: string | null;
  detectedColors: string[];
  detectedStyles: string[];
  understood: boolean;
};
