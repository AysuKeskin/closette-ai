import { Pressable, StyleSheet, View } from 'react-native';

import { useT } from '../../i18n';
import { colors, feedback, palette, radius, shadow, spacing } from '../../theme';
import { AppText } from './AppText';
import { Icon } from './Icon';

type Props = {
  onPress: () => void;
};

/**
 * Invitation to take the style quiz, for someone who has not finished it.
 *
 * Deliberately not shaped like {@link VerifyBanner}: an unverified email is a
 * problem and wears the warning wash, while this is an offer. It leads with what
 * the user gets rather than what they owe us, because the quiz is the single
 * biggest input into how good Get Ready's suggestions feel.
 */
export function StylePrompt({ onPress }: Props) {
  const { t } = useT();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${t('style.promptTitle')}. ${t('style.promptHint')}`}
      onPress={onPress}
      android_ripple={{ color: feedback.ripple, borderless: false }}
      style={({ pressed }) => [styles.card, shadow.card, pressed && styles.pressed]}
    >
      <View style={styles.iconWrap}>
        <Icon name="palette" size={24} />
      </View>
      <View style={styles.text}>
        <AppText variant="title">{t('style.promptTitle')}</AppText>
        <AppText variant="caption" tone="secondary">
          {t('style.promptHint')}
        </AppText>
        <AppText variant="label" style={styles.action}>
          {t('style.promptAction')}
        </AppText>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.md,
    backgroundColor: palette.blush,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: spacing.md,
  },
  pressed: { transform: [{ scale: feedback.pressScaleCard }], opacity: feedback.pressOpacity },
  iconWrap: {
    width: 44,
    height: 44,
    borderRadius: radius.pill,
    backgroundColor: palette.white,
    alignItems: 'center',
    justifyContent: 'center',
  },
  text: { flex: 1, gap: spacing.xs },
  action: { color: colors.secondary, fontWeight: '700' },
});
