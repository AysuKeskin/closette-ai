import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
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
  VerifyBanner,
} from '../../components/ui';
import { BuyVerdict, CLOTHING_CATEGORIES, ClothingCategory, ShouldIBuyResult } from '../../api/types';
import { useShouldIBuy } from '../../features/recommendation';
import { openVerifyEmail } from '../../navigation/navigationRef';
import { colors, radius, spacing } from '../../theme';
import type { HomeStackParamList } from '../../navigation/types';

function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

const VERDICTS: Record<BuyVerdict, { label: string; bg: string; fg: string }> = {
  buy: { label: '✓ Worth it', bg: '#E4F0E8', fg: colors.success },
  maybe: { label: 'Your call', bg: '#FBEBCF', fg: colors.warning },
  skip: { label: 'Skip it', bg: '#F7E2E2', fg: colors.danger },
};

export function ShopAssistantScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<HomeStackParamList>>();
  const check = useShouldIBuy();
  const [category, setCategory] = useState<ClothingCategory | undefined>();
  const [colors_, setColors] = useState('');
  const [styles_, setStyles] = useState('');
  const [result, setResult] = useState<ShouldIBuyResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [needsVerify, setNeedsVerify] = useState(false);

  const run = () => {
    setError(null);
    setNeedsVerify(false);
    check.mutate(
      {
        category,
        colors: splitList(colors_),
        styles: splitList(styles_),
      },
      {
        onSuccess: (data) => setResult(data),
        onError: (err) => {
          const api = toApiError(err);
          if (api.code === 'EMAIL_NOT_VERIFIED') {
            setNeedsVerify(true);
          } else {
            setError(api.message);
          }
        },
      },
    );
  };

  return (
    <Screen scroll footer={<Button label="Check my wardrobe" iconName="shop" onPress={run} loading={check.isPending} />}>
      <Header
        title="Should I buy this?"
        subtitle="See how it fits with what you already own"
        onBack={() => navigation.goBack()}
      />

      <View style={styles.form}>
        <View>
          <AppText variant="label" tone="secondary" style={styles.label}>
            Category
          </AppText>
          <View style={styles.chips}>
            {CLOTHING_CATEGORIES.map((c) => (
              <Chip key={c} label={titleCase(c)} selected={category === c} onPress={() => setCategory(c)} />
            ))}
          </View>
        </View>
        <TextField label="Colors" value={colors_} onChangeText={setColors} placeholder="black, beige" hint="Separate with commas" />
        <TextField label="Styles" value={styles_} onChangeText={setStyles} placeholder="minimal, classic" hint="Separate with commas" />
      </View>

      {check.isPending ? <LoadingState message="Comparing with your wardrobe…" /> : null}

      {needsVerify ? (
        <View style={styles.errorCard}>
          <VerifyBanner
            onPress={openVerifyEmail}
            message="The shopping assistant needs a verified email. Tap to verify."
          />
        </View>
      ) : null}

      {error ? (
        <Card style={styles.errorCard}>
          <AppText variant="label" tone="danger">{`⚠ ${error}`}</AppText>
        </Card>
      ) : null}

      {result && !check.isPending ? (
        <Card style={styles.result}>
          <View style={styles.scoreRow}>
            <AppText variant="display" tone="brand">
              {result.matchScore}%
            </AppText>
            <AppText variant="label" tone="secondary" style={styles.scoreLabel}>
              wardrobe match
            </AppText>
            <View style={[styles.verdict, { backgroundColor: VERDICTS[result.verdict].bg }]}>
              <AppText variant="label" style={{ color: VERDICTS[result.verdict].fg, fontWeight: '700' }}>
                {VERDICTS[result.verdict].label}
              </AppText>
            </View>
          </View>
          <AppText variant="body" style={styles.explanation}>
            {result.explanation}
          </AppText>

          {result.similarItems.length > 0 ? (
            <View>
              <AppText variant="label" tone="secondary" style={styles.similarTitle}>
                Similar items you own
              </AppText>
              <FlatList
                horizontal
                data={result.similarItems}
                keyExtractor={(i) => i.id}
                showsHorizontalScrollIndicator={false}
                ItemSeparatorComponent={() => <View style={{ width: spacing.md }} />}
                renderItem={({ item }) => (
                  <ItemTile width={110} title={item.name} subtitle={item.category.toLowerCase()} imageUrl={item.imageUrl} />
                )}
              />
            </View>
          ) : null}
        </Card>
      ) : null}
    </Screen>
  );
}

function splitList(value: string): string[] {
  return value
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

const styles = StyleSheet.create({
  form: { gap: spacing.lg, marginTop: spacing.md },
  label: { marginLeft: spacing.xs, marginBottom: spacing.sm },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  errorCard: { marginTop: spacing.lg },
  result: { marginTop: spacing.xl, gap: spacing.md, borderRadius: radius.lg, backgroundColor: colors.surface },
  scoreRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  verdict: { marginLeft: 'auto', paddingVertical: spacing.xs, paddingHorizontal: spacing.md, borderRadius: radius.pill },
  scoreLabel: { marginBottom: spacing.xs },
  explanation: { lineHeight: 22 },
  similarTitle: { marginBottom: spacing.sm },
});
