import type { BeautyCategory, ClothingCategory } from '../api/types';
import { useLocale } from '../store/locale';
import type { Language } from './index';

/**
 * Display names for the catalogue vocabulary.
 *
 * These values are stored in English on the item and matched on by the backend
 * and the AI service — "navy" stays "navy" in the database whatever language the
 * user reads. Only the label on screen changes, and only here.
 *
 * Anything unmapped falls back to a title-cased version of the raw value, so a
 * colour or style the AI invents still renders as a readable word rather than
 * disappearing.
 */

const CLOTHING: Record<ClothingCategory, Record<Language, string>> = {
  TOPS: { en: 'Tops', tr: 'Üst' },
  BOTTOMS: { en: 'Bottoms', tr: 'Alt' },
  DRESSES: { en: 'Dresses', tr: 'Elbise' },
  OUTERWEAR: { en: 'Outerwear', tr: 'Dış giyim' },
  SHOES: { en: 'Shoes', tr: 'Ayakkabı' },
  BAGS: { en: 'Bags', tr: 'Çanta' },
  JEWELRY: { en: 'Jewellery', tr: 'Takı' },
  ACCESSORIES: { en: 'Accessories', tr: 'Aksesuar' },
};

const BEAUTY: Record<BeautyCategory, Record<Language, string>> = {
  SKINCARE: { en: 'Skincare', tr: 'Cilt bakımı' },
  MAKEUP: { en: 'Makeup', tr: 'Makyaj' },
  HAIRCARE: { en: 'Haircare', tr: 'Saç bakımı' },
  BODYCARE: { en: 'Bodycare', tr: 'Vücut bakımı' },
  PERFUME: { en: 'Perfume', tr: 'Parfüm' },
  NAILS: { en: 'Nails', tr: 'Tırnak' },
};

const SEASON: Record<string, Record<Language, string>> = {
  spring: { en: 'Spring', tr: 'İlkbahar' },
  summer: { en: 'Summer', tr: 'Yaz' },
  fall: { en: 'Autumn', tr: 'Sonbahar' },
  winter: { en: 'Winter', tr: 'Kış' },
};

/** The colour pipeline's curated palette (ai-service/app/pipeline/color.py). */
const COLOR: Record<string, Record<Language, string>> = {
  black: { en: 'Black', tr: 'Siyah' },
  charcoal: { en: 'Charcoal', tr: 'Antrasit' },
  grey: { en: 'Grey', tr: 'Gri' },
  silver: { en: 'Silver', tr: 'Gümüş' },
  white: { en: 'White', tr: 'Beyaz' },
  cream: { en: 'Cream', tr: 'Krem' },
  beige: { en: 'Beige', tr: 'Bej' },
  tan: { en: 'Tan', tr: 'Ten rengi' },
  camel: { en: 'Camel', tr: 'Camel' },
  brown: { en: 'Brown', tr: 'Kahverengi' },
  'dusty pink': { en: 'Dusty pink', tr: 'Gül kurusu' },
  blush: { en: 'Blush', tr: 'Pudra' },
  rose: { en: 'Rose', tr: 'Gül' },
  mauve: { en: 'Mauve', tr: 'Leylak kahve' },
  burgundy: { en: 'Burgundy', tr: 'Bordo' },
  red: { en: 'Red', tr: 'Kırmızı' },
  coral: { en: 'Coral', tr: 'Mercan' },
  orange: { en: 'Orange', tr: 'Turuncu' },
  mustard: { en: 'Mustard', tr: 'Hardal' },
  yellow: { en: 'Yellow', tr: 'Sarı' },
  olive: { en: 'Olive', tr: 'Zeytin yeşili' },
  sage: { en: 'Sage', tr: 'Adaçayı yeşili' },
  green: { en: 'Green', tr: 'Yeşil' },
  teal: { en: 'Teal', tr: 'Petrol yeşili' },
  'sky blue': { en: 'Sky blue', tr: 'Bebek mavisi' },
  blue: { en: 'Blue', tr: 'Mavi' },
  navy: { en: 'Navy', tr: 'Lacivert' },
  lavender: { en: 'Lavender', tr: 'Lavanta' },
  purple: { en: 'Purple', tr: 'Mor' },
  gold: { en: 'Gold', tr: 'Altın' },
  khaki: { en: 'Khaki', tr: 'Haki' },
  ivory: { en: 'Ivory', tr: 'Fildişi' },
  denim: { en: 'Denim', tr: 'Denim' },
  pink: { en: 'Pink', tr: 'Pembe' },
  emerald: { en: 'Emerald', tr: 'Zümrüt' },
  lilac: { en: 'Lilac', tr: 'Lila' },
};

/** Style words the AI attaches to an item. */
const STYLE: Record<string, Record<Language, string>> = {
  minimal: { en: 'Minimal', tr: 'Sade' },
  classic: { en: 'Classic', tr: 'Klasik' },
  elegant: { en: 'Elegant', tr: 'Zarif' },
  casual: { en: 'Casual', tr: 'Günlük' },
  chic: { en: 'Chic', tr: 'Şık' },
  edgy: { en: 'Edgy', tr: 'İddialı' },
  boho: { en: 'Boho', tr: 'Bohem' },
  romantic: { en: 'Romantic', tr: 'Romantik' },
  sporty: { en: 'Sporty', tr: 'Spor' },
  streetwear: { en: 'Streetwear', tr: 'Sokak stili' },
  preppy: { en: 'Preppy', tr: 'Preppy' },
  feminine: { en: 'Feminine', tr: 'Feminen' },
  timeless: { en: 'Timeless', tr: 'Zamansız' },
  trendy: { en: 'Trendy', tr: 'Trend' },
  oversized: { en: 'Oversized', tr: 'Oversize' },
  fitted: { en: 'Fitted', tr: 'Vücuda oturan' },
  vintage: { en: 'Vintage', tr: 'Vintage' },
  cottagecore: { en: 'Cottagecore', tr: 'Cottagecore' },
  formal: { en: 'Formal', tr: 'Abiye' },
};

const PATTERN: Record<string, Record<Language, string>> = {
  solid: { en: 'Solid', tr: 'Düz' },
  striped: { en: 'Striped', tr: 'Çizgili' },
  floral: { en: 'Floral', tr: 'Çiçekli' },
  checked: { en: 'Checked', tr: 'Ekose' },
};

/** The four colour seasons, stored by their English name. */
const COLOR_SEASON: Record<string, Record<Language, string>> = {
  spring: { en: 'Spring', tr: 'İlkbahar' },
  summer: { en: 'Summer', tr: 'Yaz' },
  autumn: { en: 'Autumn', tr: 'Sonbahar' },
  winter: { en: 'Winter', tr: 'Kış' },
};

const COLOR_SEASON_DESC: Record<string, Record<Language, string>> = {
  spring: { en: 'Warm & bright', tr: 'Sıcak ve parlak' },
  summer: { en: 'Cool & soft', tr: 'Serin ve yumuşak' },
  autumn: { en: 'Warm & deep', tr: 'Sıcak ve derin' },
  winter: { en: 'Cool & bold', tr: 'Serin ve iddialı' },
};

/** The onboarding "dressing up" answers, stored by their English wording. */
const DRESS_UP: Record<string, Record<Language, string>> = {
  'a pretty dress': { en: 'A pretty dress', tr: 'Şık bir elbise' },
  'tailored pieces': { en: 'Tailored pieces', tr: 'Kesimli parçalar' },
  'jeans + a nice top': { en: 'Jeans + a nice top', tr: 'Kot + güzel bir üst' },
};

function titleCase(value: string, language: Language = 'en'): string {
  // Turkish's dotted capital: "ipek" must become "İpek", not "Ipek".
  const locale = language === 'tr' ? 'tr-TR' : 'en-US';
  return value.charAt(0).toLocaleUpperCase(locale) + value.slice(1);
}

function look(
  table: Record<string, Record<Language, string> | undefined>,
  value: string | null | undefined,
  language: Language,
): string {
  if (!value) return '';
  return table[value.trim().toLowerCase()]?.[language] ?? titleCase(value, language);
}

/** Display names for values stored on an item. Labels only — never sent back. */
export function useDomainLabels() {
  const language = useLocale((state) => state.language);
  return {
    clothingCategory: (value: ClothingCategory) => CLOTHING[value]?.[language] ?? titleCase(value, language),
    beautyCategory: (value: BeautyCategory) => BEAUTY[value]?.[language] ?? titleCase(value, language),
    season: (value: string) => look(SEASON, value, language),
    color: (value: string) => look(COLOR, value, language),
    style: (value: string) => look(STYLE, value, language),
    pattern: (value: string) => look(PATTERN, value, language),
    colorSeason: (value: string) => look(COLOR_SEASON, value, language),
    colorSeasonDesc: (value: string) => look(COLOR_SEASON_DESC, value, language),
    dressUp: (value: string) => look(DRESS_UP, value, language),
    /** An item's chips mix colours, styles and seasons — try each vocabulary. */
    tag: (value: string) => {
      const key = value.trim().toLowerCase();
      const table = [COLOR, STYLE, SEASON, PATTERN].find((t) => t[key]);
      return table ? table[key][language] : titleCase(value, language);
    },
  };
}
