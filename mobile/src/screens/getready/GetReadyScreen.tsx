import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useRef, useState } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import { toApiError } from '../../api/client';
import {
  AppText,
  Button,
  Card,
  Header,
  ItemTile,
  LoadingState,
  Screen,
  TextField,
} from '../../components/ui';
import type { GeneratedLook } from '../../api/types';
import { useDeleteLook, useGenerateLook, useSaveLook } from '../../features/outfits';
import { useT, type TranslationKey } from '../../i18n';
import { useDomainLabels } from '../../i18n/domain';
import { openItemDetail } from '../../navigation/navigationRef';
import { colors, radius, spacing } from '../../theme';

// A pool of occasions; we show a rotating handful each time the screen opens.
// The suggestion is also what gets sent to the stylist, so it travels in the
// user's own language — which is exactly what the model should read.
const OCCASION_POOL: TranslationKey[] = [
  'getReady.suggestions.dinner',
  'getReady.suggestions.office',
  'getReady.suggestions.brunch',
  'getReady.suggestions.firstDate',
  'getReady.suggestions.coffee',
  'getReady.suggestions.wedding',
  'getReady.suggestions.errands',
  'getReady.suggestions.dancing',
  'getReady.suggestions.wfh',
  'getReady.suggestions.walk',
  'getReady.suggestions.interview',
  'getReady.suggestions.beach',
];

function sample<T>(arr: T[], n: number): T[] {
  return [...arr].sort(() => Math.random() - 0.5).slice(0, n);
}

export function GetReadyScreen() {
  const { t: text } = useT();
  const labels = useDomainLabels();
  const generate = useGenerateLook();
  const saveLook = useSaveLook();
  const deleteLook = useDeleteLook();
  const [prompt, setPrompt] = useState('');
  const [look, setLook] = useState<GeneratedLook | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Holds the saved outfit's id while this look is saved; null means not saved.
  const [savedId, setSavedId] = useState<string | null>(null);
  const [suggestions, setSuggestions] = useState<TranslationKey[]>(() => sample(OCCASION_POOL, 3));

  // Re-roll the suggestions each time the tab is opened.
  useFocusEffect(
    useCallback(() => {
      setSuggestions(sample(OCCASION_POOL, 3));
    }, []),
  );

  // Everything already shown for this occasion, so repeated "try another" taps keep
  // moving instead of ping-ponging between two looks. Cleared when the occasion
  // changes. The stylist may still reuse a piece when the wardrobe leaves no choice.
  const shown = useRef<string[]>([]);

  const run = (text: string, keepHistory = false) => {
    const value = text.trim();
    if (!value) return;
    setError(null);
    setSavedId(null);
    setPrompt(value);
    if (!keepHistory) shown.current = [];
    generate.mutate(
      { prompt: value, occasion: value, excludeItemIds: shown.current },
      {
        onSuccess: (data) => {
          setLook(data);
          const ids = data.items.map((i) => i.id);
          shown.current = Array.from(new Set([...shown.current, ...ids]));
        },
        onError: (err) => setError(toApiError(err).message),
      },
    );
  };

  // The Save button is a toggle: save creates the outfit and keeps its id; tapping
  // again while saved deletes it (un-save) and clears the id.
  const onToggleSave = () => {
    if (!look || look.items.length === 0) return;
    if (savedId) {
      deleteLook.mutate(savedId, { onSuccess: () => setSavedId(null) });
      return;
    }
    saveLook.mutate(
      { title: look.title, occasion: prompt, rationale: look.rationale, itemIds: look.items.map((i) => i.id) },
      { onSuccess: (outfit) => setSavedId(outfit.id) },
    );
  };

  return (
    <Screen scroll>
      <Header title={text('getReady.title')} subtitle={text('getReady.subtitle')} />

      <TextField
        placeholder={text('getReady.placeholder')}
        value={prompt}
        onChangeText={setPrompt}
        multiline
        style={styles.input}
      />

      <AppText variant="label" tone="secondary" style={styles.tryLabel}>
        {text('getReady.tryOne')}
      </AppText>
      <View style={styles.suggestions}>
        {suggestions.map((key) => (
          <Pressable
            key={key}
            onPress={() => run(text(key))}
            accessibilityRole="button"
            hitSlop={8}
            style={({ pressed }) => [styles.suggestRow, pressed && styles.suggestPressed]}
          >
            <AppText style={styles.suggestArrow}>›</AppText>
            <AppText variant="body" tone="secondary" style={styles.suggestText}>
              {text(key)}
            </AppText>
          </Pressable>
        ))}
      </View>

      <Button
        label={text('getReady.createLook')}
        iconName="ai-magic"
        onPress={() => run(prompt)}
        loading={generate.isPending}
        style={styles.cta}
      />

      {error ? (
        <Card style={styles.errorCard}>
          <AppText variant="label" tone="danger">{`⚠ ${error}`}</AppText>
        </Card>
      ) : null}

      {generate.isPending ? <LoadingState message={text('getReady.styling')} /> : null}

      {look && !generate.isPending ? (
        <Card style={styles.result}>
          <AppText variant="h2">{look.title}</AppText>
          <AppText variant="body" tone="secondary" style={styles.rationale}>
            {look.rationale}
          </AppText>

          {look.items.length > 0 ? (
            <FlatList
              horizontal
              data={look.items}
              keyExtractor={(i) => i.id}
              showsHorizontalScrollIndicator={false}
              ItemSeparatorComponent={() => <View style={{ width: spacing.md }} />}
              renderItem={({ item }) => (
                <ItemTile
                  width={120}
                  title={item.name}
                  subtitle={labels.clothingCategory(item.category)}
                  imageUrl={item.imageUrl}
                  onPress={() => openItemDetail(item)}
                />
              )}
              style={styles.items}
            />
          ) : null}

          {look.items.length > 0 ? (
            <View style={styles.actions}>
              <Button
                label={savedId ? text('getReady.saved') : text('getReady.saveLook')}
                iconName={savedId ? 'love' : 'save'}
                variant={savedId ? 'secondary' : 'primary'}
                size="sm"
                onPress={onToggleSave}
                loading={saveLook.isPending || deleteLook.isPending}
                fullWidth={false}
              />
              <Button
                label={text('getReady.tryAnother')}
                iconName="ai-magic"
                variant="ghost"
                size="sm"
                onPress={() => run(prompt, true)}
                fullWidth={false}
              />
            </View>
          ) : null}
        </Card>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  input: { minHeight: 80, paddingTop: spacing.md, textAlignVertical: 'top' },
  tryLabel: { marginTop: spacing.lg, marginLeft: spacing.xs },
  // Suggestions as quiet tappable text (not pills), so they read as prompts, not buttons.
  suggestions: { marginTop: spacing.xs },
  suggestRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, paddingVertical: spacing.sm },
  suggestPressed: { opacity: 0.55 },
  suggestArrow: { color: colors.primary, fontSize: 18, fontWeight: '700' },
  suggestText: { flex: 1 },
  cta: { marginTop: spacing.xl },
  errorCard: { marginTop: spacing.lg },
  result: { marginTop: spacing.xl, gap: spacing.sm, borderRadius: radius.lg, backgroundColor: colors.surface },
  rationale: { marginBottom: spacing.sm },
  items: { marginTop: spacing.sm },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginTop: spacing.lg },
  actionBtn: { paddingHorizontal: spacing.lg },
});
