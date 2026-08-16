import { useState } from 'react';
import { FlatList, StyleSheet, View } from 'react-native';

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
import { useGenerateLook } from '../../features/outfits';
import { colors, radius, spacing } from '../../theme';

const SUGGESTIONS = [
  'Dinner with friends — relaxed but chic',
  'Office day, put-together',
  'Weekend brunch',
];

export function GetReadyScreen() {
  const generate = useGenerateLook();
  const [prompt, setPrompt] = useState('');
  const [look, setLook] = useState<GeneratedLook | null>(null);
  const [error, setError] = useState<string | null>(null);

  const run = (text: string) => {
    const value = text.trim();
    if (!value) return;
    setError(null);
    setPrompt(value);
    generate.mutate(
      { prompt: value, occasion: value },
      {
        onSuccess: (data) => setLook(data),
        onError: (err) => setError(toApiError(err).message),
      },
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
      <View style={styles.suggestions}>
        {SUGGESTIONS.map((s) => (
          <Card key={s} onPress={() => run(s)} style={styles.suggestion} padded>
            <AppText variant="label" tone="secondary">
              {s}
            </AppText>
          </Card>
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

          <View style={styles.feedback}>
            <Button label="Love it" iconName="love" variant="secondary" fullWidth={false} style={styles.fbBtn} />
            <Button label="Not for me" iconName="dislike" variant="ghost" fullWidth={false} style={styles.fbBtn} />
            <Button label="Save" iconName="save" variant="ghost" fullWidth={false} style={styles.fbBtn} />
          </View>
        </Card>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  input: { minHeight: 80, paddingTop: spacing.md, textAlignVertical: 'top' },
  suggestions: { gap: spacing.sm, marginTop: spacing.md },
  suggestion: { backgroundColor: colors.surface },
  cta: { marginTop: spacing.lg },
  errorCard: { marginTop: spacing.lg },
  result: { marginTop: spacing.xl, gap: spacing.sm, borderRadius: radius.lg },
  rationale: { marginBottom: spacing.sm },
  items: { marginTop: spacing.sm },
  feedback: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.lg, flexWrap: 'wrap' },
  fbBtn: { paddingHorizontal: spacing.lg },
});
