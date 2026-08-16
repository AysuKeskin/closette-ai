import { Pressable, StyleSheet, View } from 'react-native';

import { AppText, Card, Header, Icon, IconName, Screen, VerifyBanner } from '../../components/ui';
import { useAuth } from '../../store/auth';
import { openVerifyEmail } from '../../navigation/navigationRef';
import { colors, feedback, radius, spacing, typography } from '../../theme';

export function ProfileScreen() {
  const user = useAuth((s) => s.user);
  const signOut = useAuth((s) => s.signOut);

  const initial = (user?.username ?? user?.displayName ?? user?.email ?? '?').charAt(0).toUpperCase();

  return (
    <Screen
      scroll
      footer={
        <Pressable
          onPress={() => signOut()}
          accessibilityRole="button"
          accessibilityLabel="Log out"
          android_ripple={{ color: feedback.ripple }}
          style={({ pressed }) => [styles.logout, pressed && styles.logoutPressed]}
        >
          <AppText style={styles.logoutLabel}>Log out</AppText>
        </Pressable>
      }
    >
      <Header title="Profile" />

      <Card style={styles.identity}>
        <View style={styles.avatar}>
          <AppText variant="h1" tone="onPrimary">
            {initial}
          </AppText>
        </View>
        <View style={styles.identityText}>
          <AppText variant="title">{user?.username ?? 'Your profile'}</AppText>
          <AppText variant="label" tone="muted">
            {user?.email}
          </AppText>
          <AppText variant="caption" tone={user?.emailVerified ? 'success' : 'warning'}>
            {user?.emailVerified ? '✓ Email verified' : '• Email not verified'}
          </AppText>
        </View>
      </Card>

      {user && !user.emailVerified ? (
        <View style={styles.bannerWrap}>
          <VerifyBanner onPress={openVerifyEmail} />
        </View>
      ) : null}

      <AppText variant="label" tone="muted" style={styles.groupLabel}>
        SETTINGS
      </AppText>
      <Card padded={false} style={styles.group}>
        {ROWS.map((row, i) => (
          <Pressable
            key={row.title}
            android_ripple={{ color: feedback.ripple }}
            style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
          >
            <View style={styles.iconBadge}>
              {row.icon ? (
                <Icon name={row.icon} size={22} />
              ) : (
                <AppText style={styles.icon}>{row.emoji}</AppText>
              )}
            </View>
            <View style={styles.rowText}>
              <AppText style={styles.rowTitle}>{row.title}</AppText>
              <AppText variant="caption" tone="muted">
                {row.subtitle}
              </AppText>
            </View>
            <AppText style={styles.chevron}>›</AppText>
            {i < ROWS.length - 1 ? <View style={styles.divider} /> : null}
          </Pressable>
        ))}
      </Card>
    </Screen>
  );
}

const ROWS: { icon?: IconName; emoji?: string; title: string; subtitle: string }[] = [
  { icon: 'palette', title: 'Style preferences', subtitle: 'Tune what we recommend' },
  { icon: 'save', title: 'Saved looks', subtitle: 'Your favorites & history' },
  { icon: 'privacy', title: 'Privacy', subtitle: 'Your data stays yours' },
  { icon: 'info', title: 'About Closette', subtitle: 'Version 0.1.0' },
];

const ROW_PAD = spacing.lg;
const BADGE = 38;

const styles = StyleSheet.create({
  identity: { flexDirection: 'row', alignItems: 'center', gap: spacing.lg, marginTop: spacing.md },
  avatar: {
    width: 60,
    height: 60,
    borderRadius: radius.pill,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  identityText: { gap: spacing.xs, flex: 1 },
  bannerWrap: { marginTop: spacing.lg },

  groupLabel: { marginTop: spacing.xl, marginBottom: spacing.sm, marginLeft: spacing.sm, letterSpacing: 1 },
  group: { overflow: 'hidden' },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingHorizontal: ROW_PAD,
    paddingVertical: spacing.md,
    position: 'relative',
  },
  rowPressed: { backgroundColor: colors.surfaceAlt },
  iconBadge: {
    width: BADGE,
    height: BADGE,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceAlt,
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: { fontSize: 18 },
  rowText: { flex: 1, gap: 1 },
  rowTitle: { fontSize: typography.size.body, fontWeight: typography.weight.medium, color: colors.textPrimary },
  chevron: { fontSize: 22, color: colors.textMuted },
  // Inset hairline that starts after the icon badge, like a grouped settings list.
  divider: {
    position: 'absolute',
    left: ROW_PAD + BADGE + spacing.md,
    right: 0,
    bottom: 0,
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
  },

  // Compact, clearly-visible logout (destructive tone), centered — not full-width.
  logout: {
    alignSelf: 'center',
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.xxl,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.danger,
    backgroundColor: colors.surface,
  },
  logoutPressed: { backgroundColor: '#F7E2E2', transform: [{ scale: feedback.pressScale }] },
  logoutLabel: { color: colors.danger, fontSize: typography.size.body, fontWeight: typography.weight.semibold },
});
