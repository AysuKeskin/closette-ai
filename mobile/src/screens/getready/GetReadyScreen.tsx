import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useState } from 'react';
import { FlatList, StyleSheet, View } from 'react-native';

import { toApiError } from '../../api/client';
import {
  AppText,
  Button,
  Card,
  Chip,
  Header,
  ItemTile,
  LoadingState,
  Screen,
  TextField,
} from '../../components/ui';
import type { GeneratedLook } from '../../api/types';
import { useGenerateLook, useSaveLook } from '../../features/outfits';
import { colors, radius, spacing } from '../../theme';

// A pool of occasions; we show a rotating handful each time the screen opens.
const OCCASION_POOL = [
  'Dinner with friends',
  'Office day, put-together',
  'Weekend brunch',
  'First date',
  'Coffee run, comfy but cute',
  'A wedding guest look',
  'Rainy day errands',
  'Night out dancing',
  'Work-from-home but presentable',
  'Sunday walk in the park',
  'Job interview',
  'Beach day',
];

function sample<T>(arr: T[], n: number): T[] {
  return [...arr].sort(() => Math.random() - 0.5).slice(0, n);
}

export function GetReadyScreen() {
  const generate = useGenerateLook();
  const saveLook = useSaveLook();
  const [prompt, setPrompt] = useState('');
  const [look, setLook] = useState<GeneratedLook | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [suggestions, setSuggestions] = useState<string[]>(() => sample(OCCASION_POOL, 3));

  // Re-roll the suggestions each time the tab is opened.
  useFocusEffect(
    useCallback(() => {
      setSuggestions(sample(OCCASION_POOL, 3));
    }, []),
  );

  const run = (text: string) => {
    const value = text.trim();
    if (!value) return;
    setError(null);
    setSaved(false);
    setPrompt(value);
    generate.mutate(
      { prompt: value, occasion: value },
      {
        onSuccess: (data) => setLook(data),
        onError: (err) => setError(toApiError(err).message),
      },
    );
  };

  const onSave = () => {
    if (!look || look.items.length === 0) return;
    saveLook.mutate(
      { title: look.title, occasion: prompt, itemIds: look.items.map((i) => i.id) },
      { onSuccess: () => setSaved(true) },
    );
  };

  return (
    <Screen scroll>
      <Header title="Get Ready" subtitle="Tell me the occasion — I'll style a complete look" />

      <TextField
        placeholder="e.g. Dinner with friends, relaxed but chic"
        value={prompt}
        onChangeText={setPrompt}
        multiline
        style={styles.input}
      />

      <AppText variant="caption" tone="muted" style={styles.tryLabel}>
        Try one of these
      </AppText>
      <View style={styles.suggestions}>
        {suggestions.map((s) => (
          <Chip key={s} label={s} onPress={() => run(s)} />
        ))}
      </View>

      <Button
        label="Create my look"
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

      {generate.isPending ? <LoadingState message="Styling your look…" /> : null}

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
                  subtitle={item.category.toLowerCase()}
                  imageUrl={item.imageUrl}
                />
              )}
              style={styles.items}
            />
          ) : null}

          {look.items.length > 0 ? (
            <View style={styles.actions}>
              <Button
                label={saved ? '✓ Saved' : '♥ Save look'}
                variant={saved ? 'secondary' : 'primary'}
                onPress={onSave}
                disabled={saved}
                loading={saveLook.isPending}
                fullWidth={false}
                style={styles.actionBtn}
              />
              <Button
                label="Try another"
                variant="ghost"
                onPress={() => run(prompt)}
                fullWidth={false}
                style={styles.actionBtn}
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
  suggestions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginTop: spacing.sm },
  cta: { marginTop: spacing.xl },
  errorCard: { marginTop: spacing.lg },
  result: { marginTop: spacing.xl, gap: spacing.sm, borderRadius: radius.lg, backgroundColor: colors.surface },
  rationale: { marginBottom: spacing.sm },
  items: { marginTop: spacing.sm },
  actions: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.lg },
  actionBtn: { paddingHorizontal: spacing.lg },
});
