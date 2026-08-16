import { Pressable, StyleSheet, View } from 'react-native';

import { colors, feedback, palette, radius, spacing } from '../../theme';
import { AppText } from './AppText';
import { Icon } from './Icon';

type Props = {
  onPress: () => void;
  /** Optional override copy, e.g. when a gated action was blocked. */
  message?: string;
};

/**
 * Persistent "verify your email" nudge (soft verification). Stays visible while
 * the account is unverified; tapping opens the code screen.
 */
export function VerifyBanner({ onPress, message }: Props) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="Verify your email"
      onPress={onPress}
      android_ripple={{ color: feedback.ripple, borderless: false }}
      style={({ pressed }) => [styles.banner, pressed && styles.pressed]}
    >
      <View style={styles.iconWrap}>
        <Icon name="envelope" size={22} />
      </View>
      <View style={styles.text}>
        <AppText variant="label" style={styles.title}>
          Verify your email
        </AppText>
        <AppText variant="caption" tone="secondary" numberOfLines={2}>
          {message ?? 'Enter the code we sent to unlock everything.'}
        </AppText>
      </View>
      <AppText style={styles.chevron}>›</AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    backgroundColor: '#FBEBCF', // warm amber wash — a nudge, not an error
    borderWidth: 1,
    borderColor: '#F0D49B',
    borderRadius: radius.lg,
    padding: spacing.md,
  },
  pressed: { transform: [{ scale: feedback.pressScaleCard }], opacity: feedback.pressOpacity },
  iconWrap: {
    width: 40,
    height: 40,
    borderRadius: radius.pill,
    backgroundColor: palette.white,
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: { fontSize: 18 },
  text: { flex: 1, gap: 2 },
  title: { color: colors.warning, fontWeight: '700' },
  chevron: { fontSize: 24, color: colors.warning, marginRight: spacing.xs },
});
