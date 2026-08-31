import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { useT } from '../../i18n';
import { colors, feedback, radius, spacing, typography } from '../../theme';
import { AppText } from './AppText';
import { Icon, IconName } from './Icon';

export type SheetOption = {
  label: string;
  hint?: string;
  iconName?: IconName;
  onPress: () => void;
};

type Props = {
  visible: boolean;
  title: string;
  message?: string;
  options: SheetOption[];
  onClose: () => void;
  /** Defaults to the translated "Cancel". */
  cancelLabel?: string;
};

/**
 * An on-brand bottom sheet used in place of the OS `Alert` for choices, so the
 * dialog carries the app's pink design instead of the native grey one. Tapping
 * the scrim or Cancel dismisses; picking an option fires it then closes.
 */
export function ActionSheet({ visible, title, message, options, onClose, cancelLabel }: Props) {
  const { t } = useT();
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.scrim} onPress={onClose} accessibilityLabel={t('common.dismiss')} />
      <View style={styles.sheet}>
        <View style={styles.grabber} />
        <AppText variant="title">{title}</AppText>
        {message ? (
          <AppText variant="body" tone="secondary" style={styles.message}>
            {message}
          </AppText>
        ) : null}

        <View style={styles.options}>
          {options.map((opt) => (
            <Pressable
              key={opt.label}
              onPress={() => {
                onClose();
                opt.onPress();
              }}
              android_ripple={{ color: feedback.ripple }}
              style={({ pressed }) => [styles.option, pressed && styles.optionPressed]}
            >
              {opt.iconName ? (
                <View style={styles.iconBadge}>
                  <Icon name={opt.iconName} size={20} />
                </View>
              ) : null}
              <View style={styles.optionText}>
                <AppText style={styles.optionLabel}>{opt.label}</AppText>
                {opt.hint ? (
                  <AppText variant="caption" tone="muted">
                    {opt.hint}
                  </AppText>
                ) : null}
              </View>
              <AppText style={styles.chevron}>›</AppText>
            </Pressable>
          ))}
        </View>

        <Pressable
          onPress={onClose}
          android_ripple={{ color: feedback.ripple }}
          style={({ pressed }) => [styles.cancel, pressed && styles.cancelPressed]}
        >
          <AppText style={styles.cancelLabel}>{cancelLabel ?? t('common.cancel')}</AppText>
        </Pressable>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  scrim: { flex: 1, backgroundColor: feedback.scrim },
  sheet: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: radius.xl,
    borderTopRightRadius: radius.xl,
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
    paddingBottom: spacing.xxxl,
    gap: spacing.xs,
  },
  grabber: {
    alignSelf: 'center',
    width: 40,
    height: 5,
    borderRadius: radius.pill,
    backgroundColor: colors.border,
    marginBottom: spacing.md,
  },
  message: { marginTop: spacing.xs, lineHeight: 21 },
  options: { marginTop: spacing.lg, gap: spacing.sm },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    backgroundColor: colors.surfaceAlt,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
  },
  optionPressed: { backgroundColor: colors.accent, transform: [{ scale: feedback.pressScaleCard }] },
  iconBadge: {
    width: 38,
    height: 38,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  optionText: { flex: 1, gap: 1 },
  optionLabel: { fontSize: typography.size.body, fontWeight: typography.weight.semibold, color: colors.textPrimary },
  chevron: { fontSize: 22, color: colors.primary },
  cancel: {
    marginTop: spacing.lg,
    alignItems: 'center',
    paddingVertical: spacing.md,
    borderRadius: radius.pill,
  },
  cancelPressed: { backgroundColor: colors.surfaceAlt },
  cancelLabel: { fontSize: typography.size.body, fontWeight: typography.weight.semibold, color: colors.textSecondary },
});
