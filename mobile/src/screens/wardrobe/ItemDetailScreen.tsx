import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { Alert, FlatList, Image, Pressable, StyleSheet, View } from 'react-native';

import {
  AppText,
  Button,
  Card,
  Header,
  Icon,
  ItemTile,
  LoadingState,
  Screen,
} from '../../components/ui';
import { useDeleteItem, useItem, useSimilarItems, useToggleFavorite } from '../../features/wardrobe';
import { useT } from '../../i18n';
import { useDomainLabels } from '../../i18n/domain';
import { colors, feedback, radius, spacing, typography } from '../../theme';
import type { WardrobeStackParamList } from '../../navigation/types';

export function ItemDetailScreen() {
  const { t: text } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<WardrobeStackParamList>>();
  // Route params are a snapshot from the moment we navigated; an edit would not
  // show up here. The query re-reads the item and the edit invalidates it.
  const { item: snapshot } = useRoute<RouteProp<WardrobeStackParamList, 'ItemDetail'>>().params;
  const item = useItem(snapshot.id, snapshot).data;
  const similar = useSimilarItems(item.id);
  const toggleFav = useToggleFavorite();
  const deleteItem = useDeleteItem();
  const [fav, setFav] = useState(item.favorite);

  const onToggleFav = () => {
    setFav((f) => !f);
    toggleFav.mutate(item.id);
  };

  const onDelete = () => {
    Alert.alert(text('item.deleteTitle'), text('item.deleteBody', { name: item.name }), [
      { text: text('common.cancel'), style: 'cancel' },
      {
        text: text('common.delete'),
        style: 'destructive',
        onPress: () => deleteItem.mutate(item.id, { onSuccess: () => navigation.goBack() }),
      },
    ]);
  };

  const list = (values: string[], label: (v: string) => string) =>
    values.map(label).filter(Boolean).join(', ');

  // Mirrors the edit form field for field, so what you read is what you edit.
  const spec: { label: string; value: string }[] = [
    { label: text('common.category'), value: labels.clothingCategory(item.category) },
    { label: text('confirm.subcategory'), value: item.subcategory ?? '' },
    { label: text('confirm.colors'), value: list(item.colors, labels.color) },
    { label: text('confirm.pattern'), value: labels.pattern(item.pattern ?? '') },
    { label: text('confirm.styles'), value: list(item.styles, labels.style) },
    { label: text('confirm.seasons'), value: list(item.seasons, labels.season) },
    { label: text('item.brandLabel'), value: item.brand ?? '' },
    { label: text('item.sizeOnly'), value: item.size ?? '' },
  ].filter((row) => row.value.trim().length > 0);

  return (
    <Screen scroll>
      <Header title={text('item.detailTitle')} onBack={() => navigation.goBack()} />

      <View style={styles.imageWrap}>
        {item.imageUrl ? (
          <Image source={{ uri: item.imageUrl }} style={styles.image} resizeMode="cover" />
        ) : (
          <View style={[styles.image, styles.placeholder]}>
            <Icon name="garment" size={64} faded />
          </View>
        )}
      </View>

      <AppText variant="h2" style={styles.name}>
        {item.name}
      </AppText>
      <Card style={styles.spec}>
        {spec.map((row, i) => (
          <View key={row.label} style={[styles.specRow, i > 0 && styles.specRowDivided]}>
            <AppText variant="label" tone="secondary" style={styles.specLabel}>
              {row.label}
            </AppText>
            <AppText variant="body" style={styles.specValue}>
              {row.value}
            </AppText>
          </View>
        ))}
      </Card>

      <View style={styles.actions}>
        <Button
          label={fav ? text('item.favorited') : text('item.addToFavorites')}
          variant={fav ? 'primary' : 'secondary'}
          onPress={onToggleFav}
        />
        <Button
          label={text('common.edit')}
          variant="secondary"
          onPress={() => navigation.navigate('EditItem', { item })}
        />
      </View>

      <View style={styles.similar}>
        <AppText variant="title" style={styles.similarTitle}>
          {text('item.similarTitle')}
        </AppText>
        {similar.isLoading ? (
          <LoadingState message={text('item.findingSimilar')} />
        ) : similar.data && similar.data.length > 0 ? (
          <FlatList
            horizontal
            data={similar.data}
            keyExtractor={(i) => i.id}
            showsHorizontalScrollIndicator={false}
            ItemSeparatorComponent={() => <View style={{ width: spacing.md }} />}
            renderItem={({ item: sim }) => (
              <ItemTile
                width={124}
                title={sim.name}
                subtitle={labels.clothingCategory(sim.category)}
                imageUrl={sim.imageUrl}
                favorite={sim.favorite}
                onPress={() => navigation.push('ItemDetail', { item: sim })}
              />
            )}
          />
        ) : (
          <Card>
            <AppText variant="body" tone="secondary">
              {text('item.noSimilar')}
            </AppText>
          </Card>
        )}
      </View>

      <Pressable
        onPress={onDelete}
        style={({ pressed }) => [styles.delete, pressed && styles.deletePressed]}
      >
        <AppText style={styles.deleteLabel}>{text('item.deleteItem')}</AppText>
      </Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  imageWrap: { marginTop: spacing.md },
  image: {
    width: '100%',
    height: 360,
    borderRadius: radius.lg,
    backgroundColor: colors.surfaceAlt,
  },
  placeholder: { alignItems: 'center', justifyContent: 'center' },
  name: { marginTop: spacing.lg },
  spec: { marginTop: spacing.lg, paddingVertical: spacing.xs },
  specRow: { flexDirection: 'row', alignItems: 'baseline', paddingVertical: spacing.sm, gap: spacing.md },
  specRowDivided: { borderTopWidth: 1, borderTopColor: colors.border },
  specLabel: { width: 108 },
  specValue: { flex: 1 },
  actions: { gap: spacing.sm, marginTop: spacing.xl },
  similar: { marginTop: spacing.xxl },
  similarTitle: { marginBottom: spacing.md },
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
