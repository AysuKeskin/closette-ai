import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Image, StyleSheet, View } from 'react-native';

import { AppText, Button, Card, Header, Icon, Screen } from '../../components/ui';
import { useStylePreferences } from '../../features/preferences';
import { AESTHETIC_BY_KEY } from '../../features/aesthetics';
import { openOnboarding } from '../../navigation/navigationRef';
import { useT } from '../../i18n';
import { useDomainLabels } from '../../i18n/domain';
import { colors, radius, spacing, typography } from '../../theme';
import type { AppStackParamList } from '../../navigation/types';

// Only the swatches live here now; the season's name and description come from
// the shared vocabulary, so they read in the user's language.
const SEASON_PALETTE: Record<string, string[]> = {
  Spring: ['#F08080', '#E4C560', '#A3B18A', '#EFE7DA'],
  Summer: ['#D9A6AF', '#B9A5D1', '#8FB8DE', '#9A9A9A'],
  Autumn: ['#B08D57', '#6B6B3A', '#5E2233', '#C99A2E'],
  Winter: ['#1C1C1C', '#22314E', '#B23A48', '#F7F7F5'],
};

/** Read-only recap of the user's own onboarding answers: the colour season they
 * picked, their "dressing up" answer, and the aesthetic cards they loved (shown
 * with the real illustration + title). Not the option grid — only their picks. */
export function StyleProfileScreen() {
  const { t: text } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<AppStackParamList>>();
  const prefs = useStylePreferences();

  const season = prefs.data?.colorSeason ?? null;
  const palette = season ? SEASON_PALETTE[season] : undefined;
  const dressUp = prefs.data?.dressUp ?? null;
  const loved = (prefs.data?.lovedAesthetics ?? [])
    .map((k) => AESTHETIC_BY_KEY[k])
    .filter(Boolean);

  return (
    <Screen
      scroll
      contentStyle={styles.content}
      footer={
        <Button label={text('style.retakeQuiz')} iconName="ai-magic" onPress={() => openOnboarding()} />
      }
    >
      <Header
        title={text('style.yourStyle')}
        subtitle={text('style.profileSubtitle')}
        onBack={() => navigation.goBack()}
        stackedBack
      />

      {season ? (
        <View style={styles.block}>
          <AppText variant="label" tone="muted" style={styles.groupLabel}>
            {text('style.colourSeasonLabel')}
          </AppText>
          <View style={styles.seasonNameRow}>
            <AppText variant="title">{labels.colorSeason(season)}</AppText>
            <AppText variant="body" tone="secondary">
              · {labels.colorSeasonDesc(season)}
            </AppText>
          </View>
          {palette ? (
            <View style={styles.swatchRow}>
              {palette.map((c, i) => (
                <View key={`${c}-${i}`} style={[styles.swatch, { backgroundColor: c }]} />
              ))}
            </View>
          ) : null}
        </View>
      ) : null}

      {dressUp ? (
        <View style={styles.block}>
          <AppText variant="label" tone="muted" style={styles.groupLabel}>
            {text('style.dressUpLabel')}
          </AppText>
          <AppText variant="title">{labels.dressUp(dressUp)}</AppText>
        </View>
      ) : null}

      <AppText variant="label" tone="muted" style={styles.groupLabel}>
        {text('style.lovedLooksLabel')}
      </AppText>
      {loved.length > 0 ? (
        <View style={styles.lovedGrid}>
          {loved.map((a) => (
            <Card key={a.key} padded={false} style={styles.lovedCard}>
              <View style={styles.lovedImageWrap}>
                <Image source={a.image} style={styles.lovedImage} resizeMode="contain" />
              </View>
              <AppText style={styles.lovedName} numberOfLines={1}>
                {text(a.nameKey)}
              </AppText>
            </Card>
          ))}
        </View>
      ) : (
        <Card style={styles.empty}>
          <Icon name="love" size={20} />
          <AppText variant="body" tone="muted">
            {text('style.noLovedLooks')}
          </AppText>
        </Card>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  // Modal presentation gives ~no top safe-area inset, so add breathing room above the title.
  content: { paddingTop: spacing.xl },

  // Quiet section labels: regular weight (not bold), sitting close to their content
  // so the label reads as part of the group, not stranded above it.
  groupLabel: { marginTop: spacing.xl, marginBottom: spacing.sm, letterSpacing: 1, fontWeight: typography.weight.regular },

  // Plain info blocks (no card / border) so season & dress-up read as facts, not buttons.
  block: {},
  seasonNameRow: { flexDirection: 'row', alignItems: 'baseline', gap: spacing.xs, flexWrap: 'wrap' },
  swatchRow: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.md },
  swatch: { width: 22, height: 22, borderRadius: radius.pill, borderWidth: 1, borderColor: colors.border },

  lovedGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  lovedCard: { width: '47%', overflow: 'hidden', paddingBottom: spacing.md },
  lovedImageWrap: {
    width: '100%',
    height: 200,
    alignItems: 'center',
    justifyContent: 'flex-end',
    paddingHorizontal: spacing.sm,
    paddingTop: spacing.md,
  },
  lovedImage: { width: '100%', height: '100%' },
  lovedName: {
    marginTop: spacing.sm,
    paddingHorizontal: spacing.md,
    fontSize: typography.size.body,
    fontWeight: typography.weight.semibold,
    color: colors.textPrimary,
  },

  empty: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
});
