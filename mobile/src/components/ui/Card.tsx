import { Pressable, StyleSheet, View, ViewProps, ViewStyle } from 'react-native';

import { colors, feedback, radius, shadow, spacing } from '../../theme';

type Props = ViewProps & {
  onPress?: () => void;
  padded?: boolean;
  elevated?: boolean;
  style?: ViewStyle;
};

export function Card({ onPress, padded = true, elevated = true, style, children, ...rest }: Props) {
  const content = (
    <View
      style={[styles.card, padded && styles.padded, elevated && shadow.card, style]}
      {...rest}
    >
      {children}
    </View>
  );
  if (!onPress) return content;
  return (
    <Pressable onPress={onPress} style={({ pressed }) => pressed && styles.pressed}>
      {content}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
  },
  padded: { padding: spacing.lg },
  pressed: { opacity: feedback.pressOpacity, transform: [{ scale: feedback.pressScaleCard }] },
});
