import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { FlatList, Image, Pressable, StyleSheet, View } from 'react-native';

import { AppText, Button, Header, Icon, LoadingState, Screen, TextField } from '../../components/ui';
import type { BeautyProductCandidate } from '../../api/types';
import { useBeautySearch } from '../../features/beauty';
import { useT } from '../../i18n';
import { colors, feedback, radius, spacing } from '../../theme';
import type { BeautyStackParamList } from '../../navigation/types';

function titleCase(v: string): string {
  return v.charAt(0) + v.slice(1).toLowerCase();
}

export function BeautySearchScreen() {
  const { t: text } = useT();
  const navigation = useNavigation<NativeStackNavigationProp<BeautyStackParamList>>();
  const search = useBeautySearch();
  const [q, setQ] = useState('');
  const [results, setResults] = useState<BeautyProductCandidate[] | null>(null);

  const run = () => {
    const value = q.trim();
    if (!value) return;
    search.mutate(value, { onSuccess: (data) => setResults(data) });
  };

  return (
    <Screen scroll>
      <Header title={text('beautyForm.searchTitle')} subtitle={text('beautyForm.searchSubtitle')} onBack={() => navigation.goBack()} />

      <View style={styles.row}>
        <View style={styles.field}>
          <TextField
            placeholder={text('beautyForm.searchPlaceholder')}
            value={q}
            onChangeText={setQ}
            autoCapitalize="none"
            returnKeyType="search"
            onSubmitEditing={run}
          />
        </View>
        <Button label={text('common.search')} onPress={run} loading={search.isPending} fullWidth={false} style={styles.searchBtn} />
      </View>

      {search.isPending ? <LoadingState message={text('beautyForm.searching')} /> : null}

      {results && !search.isPending ? (
        results.length > 0 ? (
          <FlatList
            data={results}
            keyExtractor={(item, i) => `${item.barcode ?? item.productName}-${i}`}
            scrollEnabled={false}
            ItemSeparatorComponent={() => <View style={{ height: spacing.sm }} />}
            renderItem={({ item }) => (
              <Pressable
                onPress={() => navigation.navigate('ConfirmBeauty', { candidate: item })}
                android_ripple={{ color: feedback.ripple }}
                style={({ pressed }) => [styles.result, pressed && styles.resultPressed]}
              >
                {item.imageUrl ? (
                  <Image source={{ uri: item.imageUrl }} style={styles.thumb} resizeMode="cover" />
                ) : (
                  <View style={[styles.thumb, styles.thumbEmpty]}>
                    <Icon name="beauty" size={22} faded />
                  </View>
                )}
                <View style={styles.resultText}>
                  <AppText variant="label" numberOfLines={2}>
                    {item.productName}
                  </AppText>
                  <AppText variant="caption" tone="muted">
                    {[item.brand, titleCase(item.category)].filter(Boolean).join(' · ')}
                  </AppText>
                </View>
                <AppText style={styles.chevron}>›</AppText>
              </Pressable>
            )}
          />
        ) : (
          <AppText variant="body" tone="secondary" style={styles.empty}>
            {text('beautyForm.noMatches')}
          </AppText>
        )
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'flex-end', gap: spacing.sm, marginTop: spacing.md },
  field: { flex: 1 },
  searchBtn: { paddingHorizontal: spacing.lg },
  result: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    padding: spacing.sm,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  resultPressed: { backgroundColor: colors.surfaceAlt },
  thumb: { width: 52, height: 52, borderRadius: radius.sm, backgroundColor: colors.surfaceAlt },
  thumbEmpty: { alignItems: 'center', justifyContent: 'center' },
  resultText: { flex: 1, gap: 2 },
  chevron: { fontSize: 22, color: colors.textMuted },
  empty: { marginTop: spacing.xl, textAlign: 'center' },
});
