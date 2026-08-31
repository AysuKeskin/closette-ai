import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { FlatList, StyleSheet, View } from 'react-native';

import {
  Chip,
  EmptyState,
  Header,
  ItemTile,
  LoadingState,
  Screen,
} from '../../components/ui';
import { useFavoriteBeauty, useToggleBeautyFavorite } from '../../features/beauty';
import { useFavoriteItems, useToggleFavorite } from '../../features/wardrobe';
import { useT } from '../../i18n';
import { useDomainLabels } from '../../i18n/domain';
import { spacing } from '../../theme';
import { openBeautyDetail, openItemDetail } from '../../navigation/navigationRef';
import type { AppStackParamList } from '../../navigation/types';

type Tab = 'clothes' | 'beauty';

/**
 * Everything the user has hearted, in one place — clothes and beauty products
 * side by side, since a favourite is a favourite whichever shelf it lives on.
 *
 * Tapping the heart here un-favourites, which also removes the tile: this screen
 * is the only place where that is the obvious meaning of the tap.
 */
export function FavoritesScreen() {
  const { t: text, tPlural } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<AppStackParamList>>();
  const [tab, setTab] = useState<Tab>('clothes');

  const clothes = useFavoriteItems();
  const beauty = useFavoriteBeauty();
  const unfavoriteItem = useToggleFavorite();
  const unfavoriteProduct = useToggleBeautyFavorite();

  const active = tab === 'clothes' ? clothes : beauty;
  const count = active.data?.length ?? 0;

  return (
    <Screen padded={false}>
      <View style={styles.top}>
        <Header
          title={text('favorites.title')}
          subtitle={
            count > 0
              ? tPlural(tab === 'clothes' ? 'favorites.countClothes' : 'favorites.countBeauty', count)
              : text('favorites.subtitle')
          }
          onBack={() => navigation.goBack()}
        />
        <View style={styles.tabs}>
          <Chip
            label={text('favorites.clothes')}
            selected={tab === 'clothes'}
            onPress={() => setTab('clothes')}
          />
          <Chip
            label={text('favorites.beauty')}
            selected={tab === 'beauty'}
            onPress={() => setTab('beauty')}
          />
        </View>
      </View>

      {active.isLoading ? (
        <LoadingState />
      ) : active.isError ? (
        <EmptyState
          icon="warning"
          title={text('common.somethingWentWrong')}
          message={text('common.checkConnection')}
          actionLabel={text('common.retry')}
          onAction={() => active.refetch()}
        />
      ) : count === 0 ? (
        <EmptyState
          icon="love"
          title={tab === 'clothes' ? text('favorites.emptyClothes') : text('favorites.emptyBeauty')}
          message={
            tab === 'clothes' ? text('favorites.emptyClothesHint') : text('favorites.emptyBeautyHint')
          }
        />
      ) : tab === 'clothes' ? (
        <FlatList
          data={clothes.data}
          keyExtractor={(i) => i.id}
          numColumns={3}
          columnWrapperStyle={styles.column}
          contentContainerStyle={styles.grid}
          onRefresh={clothes.refetch}
          refreshing={clothes.isRefetching}
          renderItem={({ item }) => (
            <View style={styles.cell}>
              <ItemTile
                title={item.name}
                subtitle={labels.clothingCategory(item.category)}
                imageUrl={item.imageUrl}
                favorite
                onPress={() => openItemDetail(item)}
                onToggleFavorite={() => unfavoriteItem.mutate(item.id)}
              />
            </View>
          )}
        />
      ) : (
        <FlatList
          data={beauty.data}
          keyExtractor={(i) => i.id}
          numColumns={3}
          columnWrapperStyle={styles.column}
          contentContainerStyle={styles.grid}
          onRefresh={beauty.refetch}
          refreshing={beauty.isRefetching}
          renderItem={({ item }) => (
            <View style={styles.cell}>
              <ItemTile
                title={item.productName}
                subtitle={item.brand ?? labels.beautyCategory(item.category)}
                imageUrl={item.imageUrl}
                favorite
                onPress={() => openBeautyDetail(item)}
                onToggleFavorite={() => unfavoriteProduct.mutate(item.id)}
              />
            </View>
          )}
        />
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  top: { paddingHorizontal: spacing.xl, gap: spacing.md, paddingBottom: spacing.sm },
  tabs: { flexDirection: 'row', gap: spacing.sm },
  grid: { paddingHorizontal: spacing.xl, paddingTop: spacing.md, paddingBottom: spacing.xl },
  column: { gap: spacing.md, marginBottom: spacing.lg },
  cell: { width: '30%' },
});
