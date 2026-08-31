import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { AppText, Button, Chip, Header, Screen } from '../../components/ui';
import { useStylePreferences, useUpdateStylePreferences } from '../../features/preferences';
import { useT } from '../../i18n';
import { useDomainLabels } from '../../i18n/domain';
import { spacing } from '../../theme';
import type { AppStackParamList } from '../../navigation/types';

const COLORS = [
  'black', 'white', 'cream', 'beige', 'grey', 'navy', 'dusty pink', 'blush',
  'mauve', 'burgundy', 'red', 'sage', 'green', 'brown', 'camel', 'lavender',
];
const STYLES = [
  'minimal', 'feminine', 'classic', 'elegant', 'edgy', 'boho', 'chic', 'casual',
  'romantic', 'sporty', 'streetwear', 'preppy', 'timeless',
];

function toggle(list: string[], value: string): string[] {
  return list.includes(value) ? list.filter((x) => x !== value) : [...list, value];
}

export function StylePreferencesScreen() {
  const { t: text } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<AppStackParamList>>();
  const onboarding = useRoute<RouteProp<AppStackParamList, 'StylePreferences'>>().params?.onboarding;
  const prefs = useStylePreferences();
  const update = useUpdateStylePreferences();

  const [colors_, setColors] = useState<string[]>([]);
  const [styles_, setStyles] = useState<string[]>([]);

  // Seed from server once loaded.
  useEffect(() => {
    if (prefs.data) {
      setColors(prefs.data.favoriteColors ?? []);
      setStyles(prefs.data.preferredStyles ?? []);
    }
  }, [prefs.data]);

  const onSave = () => {
    update.mutate(
      { favoriteColors: colors_, preferredStyles: styles_, onboardingCompleted: true },
      { onSuccess: () => navigation.goBack() },
    );
  };

  return (
    <Screen
      scroll
      footer={
        <Button
          label={onboarding ? text('style.getStarted') : text('style.savePreferences')}
          iconName="ai-magic"
          onPress={onSave}
          loading={update.isPending}
        />
      }
    >
      <Header
        title={onboarding ? text('style.yourStyle') : text('style.preferencesTitle')}
        subtitle={
          onboarding
            ? text('style.onboardingSubtitle')
            : text('style.preferencesSubtitle')
        }
        onBack={onboarding ? undefined : () => navigation.goBack()}
      />

      <AppText variant="title" style={styles.sectionTitle}>
        {text('style.coloursYouLove')}
      </AppText>
      <View style={styles.chips}>
        {COLORS.map((c) => (
          <Chip key={c} label={labels.color(c)} selected={colors_.includes(c)} onPress={() => setColors((p) => toggle(p, c))} />
        ))}
      </View>

      <AppText variant="title" style={[styles.sectionTitle, styles.spaced]}>
        {text('style.yourStyleWords')}
      </AppText>
      <View style={styles.chips}>
        {STYLES.map((s) => (
          <Chip key={s} label={labels.style(s)} selected={styles_.includes(s)} onPress={() => setStyles((p) => toggle(p, s))} />
        ))}
      </View>

      {onboarding ? (
        <Button
          label={text('style.skipForNow')}
          variant="ghost"
          size="sm"
          fullWidth={false}
          style={styles.skip}
          onPress={() => update.mutate({ onboardingCompleted: true }, { onSuccess: () => navigation.goBack() })}
        />
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  sectionTitle: { marginTop: spacing.lg, marginBottom: spacing.md },
  spaced: { marginTop: spacing.xxl },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  skip: { alignSelf: 'center', marginTop: spacing.xl },
});
