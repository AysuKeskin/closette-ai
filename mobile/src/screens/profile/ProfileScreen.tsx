import { useState } from 'react';
import { Alert, Pressable, StyleSheet, View } from 'react-native';

import { ActionSheet, AppText, Card, Header, Icon, IconName, Screen, VerifyBanner } from '../../components/ui';
import { api, API_BASE_URL, toApiError } from '../../api/client';
import { forgetAiConsent, openLegalPage } from '../../api/consent';
import { userApi } from '../../api/endpoints';
import { useAuth } from '../../store/auth';
import { deviceLanguage, useLocale } from '../../store/locale';
import { useRetagWardrobe } from '../../features/wardrobe';
import { hasStyleProfile, useStylePreferences } from '../../features/preferences';
import { useT } from '../../i18n';
import {
  openFavorites,
  openOnboarding,
  openSavedLooks,
  openStyleProfile,
  openVerifyEmail,
} from '../../navigation/navigationRef';
import { colors, feedback, palette, radius, spacing, typography } from '../../theme';

type Row = { icon?: IconName; emoji?: string; title: string; subtitle: string; done?: boolean;
  /** Shown as a green pill where the done tick would go, for a row still worth doing. */
  cta?: string; danger?: boolean; onPress: () => void };

const APP_VERSION = '0.1.0';

export function ProfileScreen() {
  const { t: text, tPlural, language } = useT();
  const retag = useRetagWardrobe();
  const user = useAuth((s) => s.user);
  const signOut = useAuth((s) => s.signOut);
  const prefs = useStylePreferences();
  const followsDevice = useLocale((s) => s.followsDevice);
  const setLanguage = useLocale((s) => s.setLanguage);
  const useDeviceLanguage = useLocale((s) => s.useDeviceLanguage);
  const [styleSheetOpen, setStyleSheetOpen] = useState(false);
  const [languageSheetOpen, setLanguageSheetOpen] = useState(false);

  const onDeleteAccount = () => {
    Alert.alert(
      text('profile.deleteAccountTitle'),
      text('profile.deleteAccountBody'),
      [
        { text: text('common.cancel'), style: 'cancel' },
        {
          text: text('common.delete'),
          style: 'destructive',
          onPress: async () => {
            try {
              await userApi.deleteAccount();
              await signOut();
            } catch (err) {
              Alert.alert(text('profile.deleteAccountFailed'), toApiError(err).message);
            }
          },
        },
      ],
    );
  };

  const styleDone = hasStyleProfile(prefs.data);
  const savedStyleCount = prefs.data?.preferredStyles?.length ?? 0;
  const styleSummary = styleDone
    ? [
        prefs.data?.colorSeason,
        savedStyleCount > 0 ? tPlural('profile.styleCount', savedStyleCount) : undefined,
      ]
        .filter(Boolean)
        .join(' · ')
    : text('profile.styleQuizPrompt');

  // Re-cataloguing is bounded per run, so the result says whether anything is left.
  const onRetag = () => {
    if (retag.isPending) return;
    retag.mutate(undefined, {
      onSuccess: (summary) => {
        const done = summary.updated > 0
          ? tPlural('profile.retagDone', summary.updated)
          : text('profile.retagNothing');
        const more = summary.remaining > 0
          ? ` ${tPlural('profile.retagMore', summary.remaining)}`
          : '';
        Alert.alert(text('profile.retag'), done + more);
      },
      onError: (err) => Alert.alert(text('profile.retag'), toApiError(err).message),
    });
  };

  const rows: Row[] = [
    {
      // No globe in the icon set, and `info` is already the About row — so the
      // badge shows the active language code instead. It doubles as the answer
      // to "what language am I in?", which is why someone opens this row.
      emoji: language.toUpperCase(),
      title: text('profile.language'),
      subtitle: followsDevice
        ? text('profile.languageHintDevice')
        : text(language === 'tr' ? 'language.turkish' : 'language.english'),
      onPress: () => setLanguageSheetOpen(true),
    },
    {
      icon: 'ai-magic',
      title: text('profile.retag'),
      subtitle: retag.isPending ? text('profile.retagRunning') : text('profile.retagHint'),
      onPress: onRetag,
    },
    {
      icon: 'palette',
      title: text('profile.stylePreferences'),
      subtitle: styleSummary,
      done: styleDone,
      cta: styleDone ? undefined : text('profile.styleTakeTest'),
      // Once the quiz is done, tapping offers to review the saved result or retake it
      // (via the on-brand sheet); a fresh user goes straight into the quiz.
      onPress: styleDone ? () => setStyleSheetOpen(true) : () => openOnboarding(),
    },
    {
      icon: 'favorite',
      title: text('profile.favorites'),
      subtitle: text('profile.favoritesHint'),
      onPress: () => openFavorites(),
    },
    {
      icon: 'save',
      title: text('profile.savedLooks'),
      subtitle: text('profile.savedLooksHint'),
      onPress: () => openSavedLooks(),
    },
    {
      icon: 'privacy',
      title: text('profile.privacy'),
      subtitle: text('profile.privacyHint'),
      onPress: () => { void openLegalPage(`${API_BASE_URL}/privacy?lang=${language}`); },
    },
    {
      icon: 'privacy', title: text('consent.withdraw'), subtitle: text('consent.withdrawHint'),
      onPress: async () => {
        try {
          await api.put('/api/users/me/ai-consent', { accepted: false });
          forgetAiConsent();
          Alert.alert(text('consent.withdraw'), text('consent.withdrawn'));
        } catch (err) { Alert.alert(text('consent.withdraw'), toApiError(err).message); }
      },
    },
    {
      icon: 'envelope', title: text('consent.support'), subtitle: text('consent.supportHint'),
      onPress: () => { void openLegalPage(`${API_BASE_URL}/support?lang=${language}`); },
    },
    {
      icon: 'info',
      title: text('profile.about'),
      subtitle: text('profile.version', { version: APP_VERSION }),
      onPress: () =>
        Alert.alert(
          'Closette',
          `${text('profile.aboutBody')}\n${text('profile.version', { version: APP_VERSION })}`,
        ),
    },
    {
      icon: 'warning',
      title: text('profile.deleteAccount'),
      subtitle: text('profile.deleteAccountHint'),
      danger: true,
      onPress: onDeleteAccount,
    },
  ];

  const initial = (user?.username ?? user?.displayName ?? user?.email ?? '?').charAt(0).toUpperCase();

  return (
    <Screen
      scroll
      footer={
        <Pressable
          onPress={() => {
            void signOut().catch((err) => Alert.alert(text('profile.logOut'), toApiError(err).message));
          }}
          accessibilityRole="button"
          accessibilityLabel={text('profile.logOut')}
          android_ripple={{ color: feedback.ripple }}
          style={({ pressed }) => [styles.logout, pressed && styles.logoutPressed]}
        >
          <AppText style={styles.logoutLabel}>{text('profile.logOut')}</AppText>
        </Pressable>
      }
    >
      <Header title={text('profile.title')} />

      <Card style={styles.identity}>
        <View style={styles.avatar}>
          <AppText variant="h1" tone="onPrimary">
            {initial}
          </AppText>
        </View>
        <View style={styles.identityText}>
          <AppText variant="title">{user?.username ?? text('profile.yourProfile')}</AppText>
          <AppText variant="label" tone="muted">
            {user?.email}
          </AppText>
          <AppText variant="caption" tone={user?.emailVerified ? 'success' : 'warning'}>
            {user?.emailVerified ? text('profile.emailVerified') : text('profile.emailNotVerified')}
          </AppText>
        </View>
      </Card>

      {user && !user.emailVerified ? (
        <View style={styles.bannerWrap}>
          <VerifyBanner onPress={openVerifyEmail} />
        </View>
      ) : null}

      <AppText variant="label" tone="muted" style={styles.groupLabel}>
        {text('profile.settings')}
      </AppText>
      <Card padded={false} style={styles.group}>
        {rows.map((row, i) => (
          <Pressable
            key={row.title}
            onPress={row.onPress}
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
              <View style={styles.titleRow}>
                <AppText style={[styles.rowTitle, row.danger && styles.dangerTitle]}>{row.title}</AppText>
                {row.done ? (
                  <View style={styles.doneBadge}>
                    <AppText style={styles.doneCheck}>✓</AppText>
                  </View>
                ) : row.cta ? (
                  <View style={styles.ctaPill}>
                    <AppText style={styles.ctaText}>{row.cta}</AppText>
                  </View>
                ) : null}
              </View>
              <AppText variant="caption" tone={row.done ? 'success' : 'muted'}>
                {row.subtitle}
              </AppText>
            </View>
            <AppText style={styles.chevron}>›</AppText>
            {i < rows.length - 1 ? <View style={styles.divider} /> : null}
          </Pressable>
        ))}
      </Card>

      <ActionSheet
        visible={styleSheetOpen}
        title={text('profile.stylePreferences')}
        message={text('profile.styleSheetMessage')}
        options={[
          {
            label: text('profile.seePreferences'),
            hint: styleSummary,
            iconName: 'palette',
            onPress: () => openStyleProfile(),
          },
          {
            label: text('profile.retakeTest'),
            hint: text('profile.retakeTestHint'),
            iconName: 'ai-magic',
            onPress: () => openOnboarding(),
          },
        ]}
        onClose={() => setStyleSheetOpen(false)}
      />

      <ActionSheet
        visible={languageSheetOpen}
        title={text('language.title')}
        options={[
          {
            // Picking this clears the saved choice, so the app follows the phone
            // again — including later, if the phone's language changes.
            label: text('language.followDevice'),
            hint: followsDevice
              ? text('common.done')
              : text('language.currently', {
                  language: text(deviceLanguage() === 'tr' ? 'language.turkish' : 'language.english'),
                }),
            iconName: 'info',
            onPress: () => useDeviceLanguage(),
          },
          {
            label: text('language.english'),
            hint: !followsDevice && language === 'en' ? text('common.done') : undefined,
            iconName: 'info',
            onPress: () => setLanguage('en'),
          },
          {
            label: text('language.turkish'),
            hint: !followsDevice && language === 'tr' ? text('common.done') : undefined,
            iconName: 'info',
            onPress: () => setLanguage('tr'),
          },
        ]}
        onClose={() => setLanguageSheetOpen(false)}
      />
    </Screen>
  );
}

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
  icon: { fontSize: 13, fontWeight: typography.weight.bold, color: colors.textSecondary },
  rowText: { flex: 1, gap: 1 },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  rowTitle: { fontSize: typography.size.body, fontWeight: typography.weight.medium, color: colors.textPrimary },
  dangerTitle: { color: colors.danger },
  doneBadge: {
    width: 18,
    height: 18,
    borderRadius: radius.pill,
    backgroundColor: colors.success,
    alignItems: 'center',
    justifyContent: 'center',
  },
  doneCheck: { color: colors.onPrimary, fontSize: 11, fontWeight: typography.weight.bold, lineHeight: 13 },
  ctaPill: {
    backgroundColor: colors.success,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.sm,
    paddingVertical: 2,
  },
  ctaText: { color: palette.white, fontSize: 11, fontWeight: typography.weight.bold },
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
