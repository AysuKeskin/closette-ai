import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { Alert, Image, Pressable, StyleSheet, View } from 'react-native';

import { AppText, Button, Card, Chip, Header, Icon, LoadingState, Screen } from '../../components/ui';
import { useBeautyItem, useDeleteBeauty, useExplainIngredient } from '../../features/beauty';
import { useT } from '../../i18n';
import { useDomainLabels } from '../../i18n/domain';
import { colors, feedback, radius, spacing, typography } from '../../theme';
import type { BeautyStackParamList } from '../../navigation/types';

export function BeautyDetailScreen() {
  const { t: text } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<BeautyStackParamList>>();
  const { item: snapshot } = useRoute<RouteProp<BeautyStackParamList, 'BeautyDetail'>>().params;
  const item = useBeautyItem(snapshot.id, snapshot).data;
  const deleteBeauty = useDeleteBeauty();
  const explain = useExplainIngredient();
  const [active, setActive] = useState<string | null>(null);

  const onExplain = (name: string) => {
    setActive(name);
    explain.mutate(name);
  };

  const onDelete = () => {
    Alert.alert(text('beautyForm.deleteTitle'), text('beautyForm.deleteBody', { name: item.productName }), [
      { text: text('common.cancel'), style: 'cancel' },
      {
        text: text('common.delete'),
        style: 'destructive',
        onPress: () => deleteBeauty.mutate(item.id, { onSuccess: () => navigation.goBack() }),
      },
    ]);
  };

  const meta = [item.brand, labels.beautyCategory(item.category)].filter(Boolean).join(' · ');

  return (
    <Screen scroll>
      <Header
        title={text('beautyForm.detailTitle')}
        onBack={() => navigation.goBack()}
        right={
          <Button
            label={text('common.edit')}
            variant="secondary"
            fullWidth={false}
            onPress={() => navigation.navigate('EditBeauty', { item })}
          />
        }
      />

      <View style={styles.imageWrap}>
        {item.imageUrl ? (
          <Image source={{ uri: item.imageUrl }} style={styles.image} resizeMode="cover" />
        ) : (
          <View style={[styles.image, styles.placeholder]}>
            <Icon name="beauty" size={64} faded />
          </View>
        )}
      </View>

      <AppText variant="h2" style={styles.name}>
        {item.productName}
      </AppText>
      {meta ? (
        <AppText variant="label" tone="muted">
          {meta}
          {item.size ? ` · ${item.size}` : ''}
        </AppText>
      ) : null}

      {item.ingredients.length > 0 ? (
        <View style={styles.section}>
          <AppText variant="title" style={styles.sectionTitle}>
            {text('beautyForm.ingredients')}
          </AppText>
          <AppText variant="caption" tone="muted" style={styles.hint}>
            {text('beautyForm.ingredientHint')}
          </AppText>
          <View style={styles.tags}>
            {item.ingredients.map((ing, i) => (
              <Chip key={`${ing}-${i}`} label={ing} selected={active === ing} onPress={() => onExplain(ing)} />
            ))}
          </View>
          {active ? (
            <Card style={styles.explainCard}>
              <AppText variant="label" tone="brand">
                {active}
              </AppText>
              {explain.isPending ? (
                <LoadingState message={text('beautyForm.explaining')} />
              ) : (
                <AppText variant="body" tone="secondary" style={styles.explainText}>
                  {explain.data?.explanation ?? text('beautyForm.noExplanation')}
                </AppText>
              )}
            </Card>
          ) : null}
        </View>
      ) : (
        <View style={styles.section}>
          <AppText variant="body" tone="secondary">
            {text('beautyForm.noIngredientsOnFile')}
          </AppText>
        </View>
      )}

      <Pressable onPress={onDelete} style={({ pressed }) => [styles.delete, pressed && styles.deletePressed]}>
        <AppText style={styles.deleteLabel}>{text('beautyForm.deleteProduct')}</AppText>
      </Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  imageWrap: { marginTop: spacing.md },
  image: { width: '100%', height: 320, borderRadius: radius.lg, backgroundColor: colors.surfaceAlt },
  placeholder: { alignItems: 'center', justifyContent: 'center' },
  name: { marginTop: spacing.lg },
  section: { marginTop: spacing.xl },
  sectionTitle: { marginBottom: spacing.xs },
  hint: { marginBottom: spacing.md },
  tags: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  explainCard: { marginTop: spacing.md, gap: spacing.sm, backgroundColor: colors.surfaceAlt },
  explainText: { lineHeight: 22 },
  delete: {
    alignSelf: 'center',
    marginTop: spacing.xxxl,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.xl,
    borderRadius: radius.pill,
  },
  deletePressed: { backgroundColor: '#F7E2E2', transform: [{ scale: feedback.pressScale }] },
  deleteLabel: { color: colors.danger, fontSize: typography.size.label, fontWeight: typography.weight.semibold },
});
