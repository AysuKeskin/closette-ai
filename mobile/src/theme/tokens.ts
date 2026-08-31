/**
 * Semantic design tokens (NFR-02/03). Everything in the app references these by
 * meaning (colors.primary, colors.surface, spacing.lg) — never raw hex — so the
 * whole theme can be re-skinned from this one file.
 *
 * Direction: dusty-pink, feminine but not childish. Pinterest + clean beauty
 * brand + modern fashion app. Not Barbie, not a teenage diary.
 */

export const palette = {
  dustyPink: '#E7A2B6', // sweeter, pinker rose — the star of the theme
  dustyPinkDark: '#D27C95', // pressed states & deeper accents
  softRose: '#F5C6D4', // accent, warm blush
  lightRose: '#FCE4EC', // tinted surfaces & link highlights — noticeably pink
  blush: '#FBEEF3', // soft pink card fills
  warmWhite: '#FDF6F9', // background — a pink-kissed white, not grey
  white: '#FFFFFF',
  mauve: '#A0687A', // secondary accent, a pinker plum
  charcoal: '#2C2429',
  stone: '#79666E',
  fog: '#AC98A2',
  border: '#F4DCE5', // soft pink hairlines
  success: '#5B8C6E',
  danger: '#C25C6B', // rose-leaning danger so it stays on-palette
  warning: '#B98338',
};

export const colors = {
  // Brand
  primary: palette.dustyPink,
  primaryDark: palette.dustyPinkDark,
  onPrimary: '#47232F', // deep plum — reads clearly on the sweeter pink
  accent: palette.softRose,
  secondary: palette.mauve,

  // Surfaces
  background: palette.warmWhite,
  surface: palette.white,
  surfaceAlt: palette.lightRose,

  // Text
  textPrimary: palette.charcoal,
  textSecondary: palette.stone,
  textMuted: palette.fog,
  onAccent: '#54293A',

  // Lines & states
  border: palette.border,
  success: palette.success,
  danger: palette.danger,
  warning: palette.warning,
};

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 28,
  xxxl: 40,
};

export const radius = {
  sm: 8,
  md: 14,
  lg: 20,
  xl: 28,
  pill: 999,
};

export const typography = {
  family: {
    // System fonts keep the bundle light; swap here to introduce a brand face.
    regular: undefined as string | undefined,
  },
  size: {
    caption: 12,
    label: 13,
    body: 15,
    title: 17,
    h2: 22,
    h1: 28,
    display: 34,
  },
  weight: {
    regular: '400' as const,
    medium: '500' as const,
    semibold: '600' as const,
    bold: '700' as const,
  },
};

export const shadow = {
  card: {
    shadowColor: palette.charcoal,
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.08,
    shadowRadius: 16,
    elevation: 3,
  },
};

/**
 * Press feedback (NFR-04): touch has no hover, so every tappable element must
 * make "my finger is on this" unmistakable — a clear shrink + a tone shift.
 * These are deliberately pronounced so interactions feel physical, not subtle.
 */
export const feedback = {
  pressScale: 0.95, // buttons & tiles visibly shrink under the finger
  pressScaleCard: 0.97, // larger cards shrink a touch less to avoid jank
  pressOpacity: 0.9,
  ripple: 'rgba(141, 102, 112, 0.14)', // Android ripple (mauve tint)
  linkHighlight: palette.lightRose, // rose pill behind pressed text links
  scrim: 'rgba(71, 35, 47, 0.38)', // deep-plum dim behind modals/sheets, on-palette
};

export const theme = { colors, spacing, radius, typography, shadow, feedback, palette };
export type Theme = typeof theme;
