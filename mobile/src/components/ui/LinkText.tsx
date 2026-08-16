import { Pressable, StyleSheet } from 'react-native';

import { feedback, radius, spacing } from '../../theme';
import { AppText } from './AppText';

type Props = {
  /** Leading, non-emphasised copy, e.g. "New here? " */
  lead?: string;
  /** The tappable, emphasised action, e.g. "Create an account" */
  action: string;
  onPress: () => void;
};

/**
 * A tappable text link (NFR-04). Because touch has no hover, the pressed state
 * is deliberately loud: a rose pill lights up behind the text and the whole
 * target shrinks, so it's obvious the finger has landed on something tappable.
 */
export function LinkText({ lead, action, onPress }: Props) {
  return (
    <Pressable
      accessibilityRole="link"
      accessibilityLabel={`${lead ?? ''}${action}`}
      onPress={onPress}
      hitSlop={spacing.md}
      android_ripple={{ color: feedback.ripple, borderless: false }}
      style={({ pressed }) => [
        styles.wrap,
        pressed && styles.pressed,
      ]}
    >
      <AppText variant="label" tone="secondary">
        {lead}
        <AppText variant="label" tone="brand" style={styles.action}>
          {action}
        </AppText>
      </AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  wrap: {
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.pill,
    // No alignSelf: the parent container decides alignment (center / flex-end / …).
  },
  pressed: {
    backgroundColor: feedback.linkHighlight,
    transform: [{ scale: feedback.pressScale }],
  },
  action: { textDecorationLine: 'underline' },
});
