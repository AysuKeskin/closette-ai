import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  View,
  ViewStyle,
} from 'react-native';

import { colors, feedback, radius, spacing, typography } from '../../theme';
import { AppText } from './AppText';
import { Icon, IconName } from './Icon';

type Variant = 'primary' | 'secondary' | 'ghost';

type Props = {
  label: string;
  onPress?: () => void;
  variant?: Variant;
  loading?: boolean;
  disabled?: boolean;
  icon?: string; // leading glyph, e.g. "+" or "✨"
  iconName?: IconName; // custom leading icon (preferred over `icon`)
  fullWidth?: boolean;
  style?: ViewStyle;
};

export function Button({
  label,
  onPress,
  variant = 'primary',
  loading,
  disabled,
  icon,
  iconName,
  fullWidth = true,
  style,
}: Props) {
  const isDisabled = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!isDisabled, busy: !!loading }}
      onPress={onPress}
      disabled={isDisabled}
      android_ripple={isDisabled ? undefined : { color: feedback.ripple, borderless: false }}
      style={({ pressed }) => [
        styles.base,
        variantStyles[variant],
        fullWidth && { alignSelf: 'stretch' },
        pressed && !isDisabled && [styles.pressed, pressedTone[variant]],
        isDisabled && styles.disabled,
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={variant === 'primary' ? colors.onPrimary : colors.secondary} />
      ) : (
        <View style={styles.content}>
          {iconName ? (
            <Icon name={iconName} size={22} />
          ) : icon ? (
            <AppText style={[styles.icon, labelTone[variant]]}>{icon}</AppText>
          ) : null}
          <AppText style={[styles.label, labelTone[variant]]}>{label}</AppText>
        </View>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    minHeight: 52,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.xl,
    alignItems: 'center',
    justifyContent: 'center',
  },
  content: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  icon: { fontSize: typography.size.title },
  label: { fontSize: typography.size.body, fontWeight: typography.weight.semibold },
  pressed: { transform: [{ scale: feedback.pressScale }] },
  disabled: { opacity: 0.5 },
});

const variantStyles: Record<Variant, ViewStyle> = {
  primary: { backgroundColor: colors.primary },
  secondary: { backgroundColor: colors.surfaceAlt },
  ghost: { backgroundColor: 'transparent', borderWidth: 1, borderColor: colors.border },
};

// On press, deepen the fill so the shrink is reinforced by a clear tone shift.
const pressedTone: Record<Variant, ViewStyle> = {
  primary: { backgroundColor: colors.primaryDark },
  secondary: { backgroundColor: colors.accent },
  ghost: { backgroundColor: colors.surfaceAlt, borderColor: colors.primary },
};

const labelTone = {
  primary: { color: colors.onPrimary },
  secondary: { color: colors.secondary },
  ghost: { color: colors.textSecondary },
};
