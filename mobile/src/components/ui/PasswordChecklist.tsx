import { StyleSheet, View } from 'react-native';

import { colors, radius, spacing, typography } from '../../theme';
import { AppText } from './AppText';

/** A single password rule + how to test it. Kept in sync with the backend regex
 * `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,100}$` so client and server never disagree. */
export const PASSWORD_RULES: { label: string; test: (pw: string) => boolean }[] = [
  { label: 'At least 8 characters', test: (pw) => pw.length >= 8 },
  { label: 'One lowercase letter (a–z)', test: (pw) => /[a-z]/.test(pw) },
  { label: 'One uppercase letter (A–Z)', test: (pw) => /[A-Z]/.test(pw) },
  { label: 'One number (0–9)', test: (pw) => /\d/.test(pw) },
];

/** Every required rule passes → the password is accepted. */
export function isPasswordStrong(pw: string): boolean {
  return PASSWORD_RULES.every((r) => r.test(pw));
}

/** How many rules are met — drives the strength meter. */
function metCount(pw: string): number {
  return PASSWORD_RULES.reduce((n, r) => n + (r.test(pw) ? 1 : 0), 0);
}

const STRENGTH = [
  { label: 'Too weak', color: colors.danger },
  { label: 'Weak', color: colors.danger },
  { label: 'Fair', color: colors.warning },
  { label: 'Good', color: colors.warning },
  { label: 'Strong', color: colors.success },
];

type Props = { password: string };

/**
 * Live password feedback (NFR-04): a strength meter plus a per-rule checklist
 * that flips ✓ / ✗ as the user types, so they always know exactly what's still
 * missing — no guessing, no submit-then-fail.
 */
export function PasswordChecklist({ password }: Props) {
  const met = metCount(password);
  const strength = STRENGTH[met];
  const barColor = password.length === 0 ? colors.border : strength.color;

  return (
    <View style={styles.wrap}>
      <View style={styles.meter}>
        {PASSWORD_RULES.map((_, i) => (
          <View
            key={i}
            style={[
              styles.segment,
              { backgroundColor: i < met && password.length > 0 ? barColor : colors.border },
            ]}
          />
        ))}
      </View>
      {password.length > 0 ? (
        <AppText variant="caption" style={[styles.strengthLabel, { color: strength.color }]}>
          {strength.label}
        </AppText>
      ) : null}

      <View style={styles.rules}>
        {PASSWORD_RULES.map((rule) => {
          const ok = rule.test(password);
          return (
            <View key={rule.label} style={styles.row}>
              <View style={[styles.mark, ok ? styles.markOk : styles.markPending]}>
                <AppText style={[styles.markGlyph, { color: ok ? colors.onPrimary : colors.textMuted }]}>
                  {ok ? '✓' : '✕'}
                </AppText>
              </View>
              <AppText
                variant="caption"
                style={{ color: ok ? colors.textPrimary : colors.textMuted }}
              >
                {rule.label}
              </AppText>
            </View>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.sm, marginTop: spacing.xs },
  meter: { flexDirection: 'row', gap: spacing.xs },
  segment: { flex: 1, height: 5, borderRadius: radius.pill },
  strengthLabel: { fontWeight: typography.weight.semibold },
  rules: { gap: spacing.xs, marginTop: spacing.xs },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  mark: {
    width: 18,
    height: 18,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  markOk: { backgroundColor: colors.success },
  markPending: { backgroundColor: colors.surfaceAlt },
  markGlyph: { fontSize: 11, fontWeight: typography.weight.bold, lineHeight: 14 },
});
