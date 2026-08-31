import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { FlatList, StyleSheet, View } from 'react-native';

import {
  AppText,
  Button,
  Chip,
  EmptyState,
  Header,
  ItemTile,
  LoadingState,
  Screen,
  TextField,
} from '../../components/ui';
import { CLOTHING_CATEGORIES, ClothingCategory } from '../../api/types';
import { useWardrobe } from '../../features/wardrobe';
import { useT } from '../../i18n';
import { useDomainLabels } from '../../i18n/domain';
import { spacing } from '../../theme';
import type { WardrobeStackParamList } from '../../navigation/types';

export function WardrobeListScreen() {
  const { t: text, tPlural } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<WardrobeStackParamList>>();
  const [category, setCategory] = useState<ClothingCategory | undefined>(undefined);
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const [search, setSearch] = useState('');

  const { data, isLoading, isError, refetch, isRefetching } = useWardrobe({
    category,
    favorite: favoritesOnly || undefined,
    q: search.trim() || undefined,
  });
  const filtered = Boolean(category || favoritesOnly || search.trim());

  return (
    <Screen padded={false}>
      <View style={styles.top}>
        <Header
          title={text('wardrobe.title')}
          subtitle={text('wardrobe.subtitle')}
          right={
            <Button
              label={text('common.add')}
              icon="+"
              fullWidth={false}
              onPress={() => navigation.navigate('AddItem')}
            />
          }
        />
        <TextField
          placeholder={text('wardrobe.searchPlaceholder')}
          value={search}
          onChangeText={setSearch}
          returnKeyType="search"
        />
        <View style={styles.filters}>
          <FlatList
            horizontal
            data={['ALL', 'FAVORITES', ...CLOTHING_CATEGORIES]}
            keyExtractor={(c) => c}
            showsHorizontalScrollIndicator={false}
            ItemSeparatorComponent={() => <View style={{ width: spacing.sm }} />}
            renderItem={({ item }) => {
              if (item === 'FAVORITES') {
                return (
                  <Chip
                    label={text('wardrobe.favoritesFilter')}
                    selected={favoritesOnly}
                    onPress={() => setFavoritesOnly((on) => !on)}
                  />
                );
              }
              return (
                <Chip
                  label={item === 'ALL' ? text('common.all') : labels.clothingCategory(item as ClothingCategory)}
                  selected={item === 'ALL' ? !category : category === item}
                  onPress={() => setCategory(item === 'ALL' ? undefined : (item as ClothingCategory))}
                />
              );
            }}
          />
        </View>
      </View>

      {isLoading ? (
        <LoadingState message={text('wardrobe.loading')} />
      ) : isError ? (
        <EmptyState
          icon="warning"
          title={text('wardrobe.loadError')}
          message={text('common.checkConnection')}
          actionLabel={text('common.retry')}
          onAction={() => refetch()}
        />
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon="wardrobe"
          title={filtered ? text('wardrobe.emptyFilteredTitle') : text('wardrobe.emptyTitle')}
          message={filtered ? text('wardrobe.emptyFilteredMessage') : text('wardrobe.emptyMessage')}
          actionLabel={filtered ? undefined : text('wardrobe.emptyAction')}
          onAction={filtered ? undefined : () => navigation.navigate('AddItem')}
        />
      ) : (
        <FlatList
          data={data}
          keyExtractor={(i) => i.id}
          numColumns={3}
          columnWrapperStyle={styles.column}
          contentContainerStyle={styles.grid}
          onRefresh={refetch}
          refreshing={isRefetching}
          renderItem={({ item }) => (
            <View style={styles.cell}>
              <ItemTile
                title={item.name}
                subtitle={labels.clothingCategory(item.category)}
                imageUrl={item.imageUrl}
                favorite={item.favorite}
                onPress={() => navigation.navigate('ItemDetail', { item })}
              />
            </View>
          )}
          ListFooterComponent={
            data.length > 0 ? (
              <AppText variant="caption" tone="muted" center style={styles.count}>
                {tPlural('wardrobe.countItems', data.length)}
              </AppText>
            ) : null
          }
        />
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  top: { paddingHorizontal: spacing.xl, gap: spacing.md },
  filters: { marginTop: spacing.xs, marginBottom: spacing.sm },
  grid: { paddingHorizontal: spacing.xl, paddingTop: spacing.md, paddingBottom: spacing.xl },
  column: { gap: spacing.md, marginBottom: spacing.lg },
  cell: { width: '30%' },
  count: { marginTop: spacing.lg },
});
