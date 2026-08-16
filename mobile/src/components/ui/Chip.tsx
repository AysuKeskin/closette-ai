import { Pressable, StyleSheet } from 'react-native';

import { colors, feedback, radius, spacing, typography } from '../../theme';
import { AppText } from './AppText';

type Props = {
  label: string;
  selected?: boolean;
  onPress?: () => void;
};

/** A pill used for filters and tags. Selection is not conveyed by color alone —
 * the text weight also changes (NFR-04). */
export function Chip({ label, selected, onPress }: Props) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected: !!selected }}
      onPress={onPress}
      android_ripple={onPress ? { color: feedback.ripple, borderless: false } : undefined}
      style={({ pressed }) => [
        styles.chip,
        selected ? styles.selected : styles.unselected,
        pressed && !!onPress && styles.pressed,
      ]}
    >
      <AppText
        style={[
          styles.label,
          { color: selected ? colors.onPrimary : colors.textSecondary },
          selected && { fontWeight: typography.weight.semibold },
        ]}
      >
        {label}
      </AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
    borderRadius: radius.pill,
    borderWidth: 1,
  },
  selected: { backgroundColor: colors.primary, borderColor: colors.primary },
  unselected: { backgroundColor: colors.surface, borderColor: colors.border },
  pressed: { backgroundColor: colors.surfaceAlt, borderColor: colors.primary, transform: [{ scale: feedback.pressScale }] },
  label: { fontSize: typography.size.label },
});
