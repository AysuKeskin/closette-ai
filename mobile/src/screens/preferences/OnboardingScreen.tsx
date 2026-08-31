import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useMemo, useState } from 'react';
import { Image, Pressable, StyleSheet, View } from 'react-native';

import { AppText, Button, Icon, Screen, SwipeDeck } from '../../components/ui';
import { useUpdateStylePreferences } from '../../features/preferences';
import { AESTHETICS } from '../../features/aesthetics';
import { useT, type TranslationKey } from '../../i18n';
import { useDomainLabels } from '../../i18n/domain';
import { colors, feedback, radius, spacing, typography } from '../../theme';
import type { AppStackParamList } from '../../navigation/types';

// `key` and `value` are what get stored and sent to the AI, so they stay in
// English whatever the user reads; only the labels are translated.
type Season = { key: string; palette: string[]; colors: string[] };
const SEASONS: Season[] = [
  { key: 'Spring', palette: ['#F08080', '#E4C560', '#A3B18A', '#EFE7DA'], colors: ['coral', 'yellow', 'sage', 'cream'] },
  { key: 'Summer', palette: ['#D9A6AF', '#B9A5D1', '#8FB8DE', '#9A9A9A'], colors: ['dusty pink', 'lavender', 'sky blue', 'grey'] },
  { key: 'Autumn', palette: ['#B08D57', '#6B6B3A', '#5E2233', '#C99A2E'], colors: ['camel', 'olive', 'burgundy', 'mustard'] },
  { key: 'Winter', palette: ['#1C1C1C', '#22314E', '#B23A48', '#F7F7F5'], colors: ['black', 'navy', 'red', 'white'] },
];

type Choice = { value: string; labelKey: TranslationKey; tags: string[] };
const DRESS_UP: Choice[] = [
  { value: 'A pretty dress', labelKey: 'style.dressUp.dress', tags: ['feminine', 'elegant'] },
  { value: 'Tailored pieces', labelKey: 'style.dressUp.tailored', tags: ['classic', 'minimal'] },
  { value: 'Jeans + a nice top', labelKey: 'style.dressUp.jeans', tags: ['casual', 'chic'] },
];

function Swatches({ palette, size = 26 }: { palette: string[]; size?: number }) {
  return (
    <View style={styles.swatchRow}>
      {palette.map((c, i) => (
        <View key={`${c}-${i}`} style={{ width: size, height: size, borderRadius: 999, backgroundColor: c, borderWidth: 1, borderColor: colors.border }} />
      ))}
    </View>
  );
}

export function OnboardingScreen() {
  const { t: text, tPlural } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<AppStackParamList>>();
  const update = useUpdateStylePreferences();

  const [step, setStep] = useState(0);
  const [season, setSeason] = useState<Season | null>(null);
  const [tags, setTags] = useState<Set<string>>(new Set());
  const [swiped, setSwiped] = useState(0);
  const [loved, setLoved] = useState<string[]>([]);
  const [dressUp, setDressUp] = useState<string | null>(null);

  const addTags = (t: string[]) => setTags((prev) => new Set([...prev, ...t]));

  const finish = () => {
    update.mutate(
      {
        colorSeason: season?.key,
        favoriteColors: season?.colors ?? [],
        preferredStyles: Array.from(tags),
        lovedAesthetics: loved,
        dressUp,
        onboardingCompleted: true,
      },
      { onSuccess: () => navigation.goBack() },
    );
  };

  const skip = () =>
    update.mutate({ onboardingCompleted: true }, { onSuccess: () => navigation.goBack() });

  // Each aesthetic photo counts as its own step, so the bar creeps forward on
  // every swipe rather than jumping in one block. Units: season + dress-up + one per card.
  const progress = useMemo(() => {
    const total = 2 + AESTHETICS.length;
    const done = step === 0 ? 0 : step === 1 ? 1 : step === 2 ? 2 + swiped : total;
    return done / total;
  }, [step, swiped]);

  return (
    <Screen scroll>
      <View style={styles.progressTrack}>
        <View style={[styles.progressFill, { width: `${progress * 100}%` }]} />
      </View>

      {step === 0 ? (
        <View>
          <Icon name="ai-magic" size={34} />
          <AppText variant="h1" style={styles.q}>{text('style.colourSeasonQuestion')}</AppText>
          <AppText variant="body" tone="secondary" style={styles.sub}>
            {text('style.colourSeasonHint')}
          </AppText>
          <View style={styles.grid}>
            {SEASONS.map((s) => (
              <Pressable
                key={s.key}
                onPress={() => {
                  setSeason(s);
                  setStep(1);
                }}
                style={({ pressed }) => [styles.seasonCard, season?.key === s.key && styles.selected, pressed && styles.pressed]}
              >
                <Swatches palette={s.palette} />
                <AppText variant="title" style={styles.seasonName}>{labels.colorSeason(s.key)}</AppText>
                <AppText variant="caption" tone="muted">{labels.colorSeasonDesc(s.key)}</AppText>
              </Pressable>
            ))}
          </View>
        </View>
      ) : null}

      {step === 1 ? (
        <StepChoices
          title={text('style.onboardingDressUp')}
          choices={DRESS_UP}
          onPick={(c) => {
            addTags(c.tags);
            setDressUp(c.value);
            setStep(2);
          }}
          onBack={() => setStep(0)}
        />
      ) : null}

      {step === 2 ? (
        <View style={styles.swipeStep}>
          <AppText variant="h1" style={styles.q}>{text('style.onboardingLooks')}</AppText>
          <AppText variant="body" tone="secondary" style={styles.sub}>
            {text('style.onboardingLooksHint')}
          </AppText>
          <View style={styles.deckArea}>
            <SwipeDeck
              data={AESTHETICS}
              onSwipe={(item, dir) => {
                if (dir === 'right') {
                  addTags(item.tags);
                  setLoved((prev) => [...prev, item.key]);
                }
                setSwiped((n) => n + 1);
              }}
              onEmpty={() => setStep(3)}
              renderCard={(item) => (
                <View style={styles.aestheticCard}>
                  <View style={styles.aestheticImageWrap}>
                    <Image source={item.image} style={styles.aestheticImage} resizeMode="contain" />
                  </View>
                  <AppText variant="display" tone="brand" style={styles.aestheticName}>{text(item.nameKey)}</AppText>
                  <AppText variant="body" tone="secondary" style={styles.aestheticVibe}>{text(item.vibeKey)}</AppText>
                  <Swatches palette={item.palette} size={22} />
                </View>
              )}
            />
          </View>
        </View>
      ) : null}

      {step === 3 ? (
        <View style={styles.doneStep}>
          <Icon name="ai-magic" size={40} />
          <AppText variant="h1" style={styles.q}>{text('style.onboardingDone')}</AppText>
          <AppText variant="body" tone="secondary" style={styles.sub}>
            {text('style.onboardingSummary', {
              season: season ? `${labels.colorSeason(season.key)} · ` : '',
              count: tPlural('style.lookCount', loved.length),
            })}
          </AppText>
          <Button label={text('style.startStyling')} iconName="ai-magic" onPress={finish} loading={update.isPending} style={styles.finishBtn} />
        </View>
      ) : null}

      {step < 3 ? (
        <Button label={text('style.skipForNow')} variant="ghost" size="sm" fullWidth={false} style={styles.skip} onPress={skip} />
      ) : null}
    </Screen>
  );
}

function StepChoices({ title, choices, onPick, onBack }: { title: string; choices: Choice[]; onPick: (c: Choice) => void; onBack: () => void }) {
  const { t: text } = useT();
  return (
    <View>
      <Pressable onPress={onBack} hitSlop={12} style={styles.back}>
        <AppText variant="h2" tone="secondary">‹</AppText>
      </Pressable>
      <AppText variant="h1" style={styles.q}>{title}</AppText>
      <View style={styles.choiceGrid}>
        {choices.map((c) => (
          <Pressable
            key={c.value}
            onPress={() => onPick(c)}
            style={({ pressed }) => [styles.choice, pressed && styles.pressed]}
          >
            <AppText variant="title">{text(c.labelKey)}</AppText>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  progressTrack: { height: 6, borderRadius: 999, backgroundColor: colors.surfaceAlt, marginTop: spacing.md, overflow: 'hidden' },
  progressFill: { height: 6, borderRadius: 999, backgroundColor: colors.primary },
  q: { marginTop: spacing.xl },
  sub: { marginTop: spacing.sm, marginBottom: spacing.lg, lineHeight: 22 },
  swatchRow: { flexDirection: 'row', gap: spacing.xs },
  grid: { gap: spacing.md },
  seasonCard: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    gap: spacing.sm,
  },
  selected: { borderColor: colors.primary, borderWidth: 2 },
  pressed: { backgroundColor: colors.surfaceAlt, transform: [{ scale: 0.99 }] },
  seasonName: { marginTop: spacing.xs },
  choiceGrid: { gap: spacing.md, marginTop: spacing.lg },
  choice: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: spacing.xl,
    paddingHorizontal: spacing.lg,
    alignItems: 'center',
  },
  back: { position: 'absolute', top: spacing.md, left: 0, zIndex: 2 },
  swipeStep: { minHeight: 600 },
  deckArea: { height: 460, marginTop: spacing.lg, justifyContent: 'center' },
  aestheticCard: {
    flex: 1,
    backgroundColor: colors.surface,
    borderRadius: radius.xl,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: spacing.lg,
    paddingHorizontal: spacing.xl,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.sm,
    shadowColor: '#2C2429',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.1,
    shadowRadius: 20,
    elevation: 4,
  },
  aestheticImageWrap: {
    width: '100%',
    flex: 1,
    borderRadius: radius.lg,
    backgroundColor: colors.surfaceAlt,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  aestheticImage: { width: '100%', height: '100%' },
  aestheticVibe: { textAlign: 'center', marginBottom: spacing.xs },
  aestheticName: { marginTop: spacing.xs },
  doneStep: { alignItems: 'center', paddingTop: spacing.xxxl, gap: spacing.sm },
  finishBtn: { marginTop: spacing.xl, alignSelf: 'stretch' },
  skip: { alignSelf: 'center', marginTop: spacing.xxl },
});
