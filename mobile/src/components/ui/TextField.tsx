import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, TextInputProps, View } from 'react-native';

import { colors, radius, spacing, typography } from '../../theme';
import { AppText } from './AppText';

type Props = TextInputProps & {
  label?: string;
  error?: string | null;
  hint?: string;
};

export function TextField({
  label,
  error,
  hint,
  style,
  onFocus,
  onBlur,
  secureTextEntry,
  ...rest
}: Props) {
  const [focused, setFocused] = useState(false);
  // Password fields get a Show/Hide toggle; start hidden.
  const [hidden, setHidden] = useState(true);
  const secure = !!secureTextEntry;

  return (
    <View style={styles.wrap}>
      {label ? (
        <AppText variant="label" tone="secondary" style={styles.label}>
          {label}
        </AppText>
      ) : null}
      <View style={styles.inputRow}>
        <TextInput
          placeholderTextColor={colors.textMuted}
          secureTextEntry={secure ? hidden : false}
          style={[
            styles.input,
            secure && styles.inputWithToggle,
            focused && styles.focused,
            !!error && styles.errored,
            style,
          ]}
          {...rest}
          onFocus={(e) => {
            setFocused(true);
            onFocus?.(e);
          }}
          onBlur={(e) => {
            setFocused(false);
            onBlur?.(e);
          }}
        />
        {secure ? (
          <Pressable
            onPress={() => setHidden((h) => !h)}
            hitSlop={spacing.sm}
            accessibilityRole="button"
            accessibilityLabel={hidden ? 'Show password' : 'Hide password'}
            style={styles.toggle}
          >
            <AppText variant="label" tone="brand">
              {hidden ? 'Show' : 'Hide'}
            </AppText>
          </Pressable>
        ) : null}
      </View>
      {error ? (
        <AppText variant="caption" tone="danger" style={styles.helper}>
          {`⚠ ${error}`}
        </AppText>
      ) : hint ? (
        <AppText variant="caption" tone="muted" style={styles.helper}>
          {hint}
        </AppText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.xs },
  label: { marginLeft: spacing.xs },
  inputRow: { position: 'relative', justifyContent: 'center' },
  input: {
    minHeight: 52,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.lg,
    fontSize: typography.size.body,
    color: colors.textPrimary,
  },
  inputWithToggle: { paddingRight: 64 },
  focused: { borderColor: colors.primary },
  errored: { borderColor: colors.danger },
  toggle: {
    position: 'absolute',
    right: spacing.md,
    height: '100%',
    justifyContent: 'center',
    paddingHorizontal: spacing.xs,
  },
  helper: { marginLeft: spacing.xs },
});
