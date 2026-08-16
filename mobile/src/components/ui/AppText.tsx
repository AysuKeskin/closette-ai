import { Text as RNText, TextProps, TextStyle } from 'react-native';

import { colors, typography } from '../../theme';

type Variant = 'display' | 'h1' | 'h2' | 'title' | 'body' | 'label' | 'caption';
type Tone = 'primary' | 'secondary' | 'muted' | 'brand' | 'danger' | 'success' | 'warning' | 'onPrimary';

const variantStyle: Record<Variant, TextStyle> = {
  display: { fontSize: typography.size.display, fontWeight: typography.weight.bold },
  h1: { fontSize: typography.size.h1, fontWeight: typography.weight.bold },
  h2: { fontSize: typography.size.h2, fontWeight: typography.weight.semibold },
  title: { fontSize: typography.size.title, fontWeight: typography.weight.semibold },
  body: { fontSize: typography.size.body, fontWeight: typography.weight.regular },
  label: { fontSize: typography.size.label, fontWeight: typography.weight.medium },
  caption: { fontSize: typography.size.caption, fontWeight: typography.weight.regular },
};

const toneColor: Record<Tone, string> = {
  primary: colors.textPrimary,
  secondary: colors.textSecondary,
  muted: colors.textMuted,
  brand: colors.secondary,
  danger: colors.danger,
  success: colors.success,
  warning: colors.warning,
  onPrimary: colors.onPrimary,
};

type Props = TextProps & {
  variant?: Variant;
  tone?: Tone;
  center?: boolean;
};

export function AppText({ variant = 'body', tone = 'primary', center, style, ...rest }: Props) {
  return (
    <RNText
      style={[variantStyle[variant], { color: toneColor[tone] }, center && { textAlign: 'center' }, style]}
      {...rest}
    />
  );
}
