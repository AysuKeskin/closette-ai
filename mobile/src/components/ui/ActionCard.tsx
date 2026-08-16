import { Pressable, StyleSheet, View, ViewStyle } from 'react-native';

import { colors, feedback, palette, radius, shadow, spacing } from '../../theme';
import { AppText } from './AppText';
import { Icon, IconName } from './Icon';

type Props = {
  /** Custom icon (preferred). Falls back to `emoji` if omitted. */
  icon?: IconName;
  emoji?: string;
  title: string;
  subtitle: string;
  onPress: () => void;
  tone?: 'pink' | 'rose' | 'plain';
  style?: ViewStyle;
};

const toneBg: Record<NonNullable<Props['tone']>, string> = {
  pink: palette.softRose,
  rose: palette.blush,
  plain: colors.surface,
};

/** The big tappable tiles on Home (FR-15). Large touch target, icon + two lines. */
export function ActionCard({ icon, emoji, title, subtitle, onPress, tone = 'plain', style }: Props) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${title}. ${subtitle}`}
      onPress={onPress}
      android_ripple={{ color: feedback.ripple, borderless: false }}
      style={({ pressed }) => [
        styles.card,
        shadow.card,
        { backgroundColor: toneBg[tone] },
        pressed && styles.pressed,
        style,
      ]}
    >
      <View style={styles.emojiBadge}>
        {icon ? <Icon name={icon} size={30} /> : <AppText variant="h2">{emoji}</AppText>}
      </View>
      <View style={styles.text}>
        <AppText variant="title">{title}</AppText>
        <AppText variant="label" tone="secondary" style={styles.subtitle}>
          {subtitle}
        </AppText>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    flex: 1,
    minHeight: 148,
    borderRadius: radius.lg,
    padding: spacing.lg,
    justifyContent: 'space-between',
    borderWidth: 1,
    borderColor: colors.border,
  },
  emojiBadge: {
    width: 48,
    height: 48,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  text: { gap: spacing.xs },
  subtitle: { lineHeight: 18 },
  pressed: {
    transform: [{ scale: feedback.pressScaleCard }],
    borderColor: colors.primary,
    opacity: feedback.pressOpacity,
  },
});
