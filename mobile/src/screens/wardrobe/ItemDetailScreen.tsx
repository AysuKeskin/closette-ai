import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { Alert, FlatList, Image, Pressable, StyleSheet, View } from 'react-native';

import {
  AppText,
  Button,
  Card,
  Chip,
  Header,
  Icon,
  ItemTile,
  LoadingState,
  Screen,
} from '../../components/ui';
import { useDeleteItem, useSimilarItems, useToggleFavorite } from '../../features/wardrobe';
import { colors, feedback, radius, spacing, typography } from '../../theme';
import type { WardrobeStackParamList } from '../../navigation/types';

function titleCase(v: string): string {
  return v.charAt(0) + v.slice(1).toLowerCase();
}

export function ItemDetailScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<WardrobeStackParamList>>();
  const { item } = useRoute<RouteProp<WardrobeStackParamList, 'ItemDetail'>>().params;
  const similar = useSimilarItems(item.id);
  const toggleFav = useToggleFavorite();
  const deleteItem = useDeleteItem();
  const [fav, setFav] = useState(item.favorite);

  const onToggleFav = () => {
    setFav((f) => !f);
    toggleFav.mutate(item.id);
  };

  const onDelete = () => {
    Alert.alert('Delete item?', `"${item.name}" will be removed. This can't be undone.`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: () => deleteItem.mutate(item.id, { onSuccess: () => navigation.goBack() }),
      },
    ]);
  };

  const meta = [titleCase(item.category), item.subcategory].filter(Boolean).join(' · ');
  const tags = [...item.colors, ...item.styles, ...item.seasons];

  return (
    <Screen scroll>
      <Header title="Item" onBack={() => navigation.goBack()} />

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
      {meta ? (
        <AppText variant="label" tone="muted">
          {meta}
        </AppText>
      ) : null}
      {item.brand ? (
        <AppText variant="label" tone="secondary" style={styles.brand}>
          {item.brand}
          {item.size ? ` · Size ${item.size}` : ''}
        </AppText>
      ) : null}

      {tags.length > 0 ? (
        <View style={styles.tags}>
          {tags.map((t, i) => (
            <Chip key={`${t}-${i}`} label={titleCase(t)} />
          ))}
        </View>
      ) : null}

      <View style={styles.actions}>
        <Button
          label={fav ? '♥ Favorited' : '♡ Add to favorites'}
          variant={fav ? 'primary' : 'secondary'}
          onPress={onToggleFav}
        />
      </View>

      <View style={styles.similar}>
        <AppText variant="title" style={styles.similarTitle}>
          Similar in your closet
        </AppText>
        {similar.isLoading ? (
          <LoadingState message="Finding similar pieces…" />
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
                subtitle={titleCase(sim.category)}
                imageUrl={sim.imageUrl}
                favorite={sim.favorite}
                onPress={() => navigation.push('ItemDetail', { item: sim })}
              />
            )}
          />
        ) : (
          <Card>
            <AppText variant="body" tone="secondary">
              Nothing similar yet — add more pieces and we'll match them by look.
            </AppText>
          </Card>
        )}
      </View>

      <Pressable
        onPress={onDelete}
        style={({ pressed }) => [styles.delete, pressed && styles.deletePressed]}
      >
        <AppText style={styles.deleteLabel}>Delete item</AppText>
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
  brand: { marginTop: spacing.xs },
  tags: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginTop: spacing.md },
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
