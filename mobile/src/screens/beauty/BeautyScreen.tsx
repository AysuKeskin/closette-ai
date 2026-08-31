import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { FlatList, StyleSheet, View } from 'react-native';

import {
  AppText,
  Button,
  Card,
  Chip,
  EmptyState,
  Header,
  ItemTile,
  LoadingState,
  Screen,
} from '../../components/ui';
import { BEAUTY_CATEGORIES, type BeautyCategory } from '../../api/types';
import { useBeauty } from '../../features/beauty';
import { useT } from '../../i18n';
import { useDomainLabels } from '../../i18n/domain';
import { spacing } from '../../theme';
import type { BeautyStackParamList } from '../../navigation/types';

export function BeautyScreen() {
  const { t: text } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<BeautyStackParamList>>();
  const [category, setCategory] = useState<BeautyCategory | undefined>(undefined);
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const { data, isLoading, isError, refetch, isRefetching } = useBeauty({
    category,
    favorite: favoritesOnly || undefined,
  });
  const filtered = Boolean(category || favoritesOnly);

  return (
    <Screen padded={false}>
      <View style={styles.top}>
        <Header
          title={text('beauty.title')}
          subtitle={text('beauty.subtitle')}
          right={
            <Button
              label={text('common.add')}
              icon="+"
              fullWidth={false}
              onPress={() => navigation.navigate('AddBeauty')}
            />
          }
        />
        <FlatList
          horizontal
          data={['ALL', 'FAVORITES', ...BEAUTY_CATEGORIES]}
          keyExtractor={(c) => c}
          showsHorizontalScrollIndicator={false}
          ItemSeparatorComponent={() => <View style={{ width: spacing.sm }} />}
          renderItem={({ item }) => {
            if (item === 'FAVORITES') {
              return (
                <Chip
                  label={text('beauty.favoritesFilter')}
                  selected={favoritesOnly}
                  onPress={() => setFavoritesOnly((on) => !on)}
                />
              );
            }
            return (
              <Chip
                label={item === 'ALL' ? text('common.all') : labels.beautyCategory(item as BeautyCategory)}
                selected={item === 'ALL' ? !category : category === item}
                onPress={() => setCategory(item === 'ALL' ? undefined : (item as BeautyCategory))}
              />
            );
          }}
        />
      </View>

      {isLoading ? (
        <LoadingState message={text('beauty.loading')} />
      ) : isError ? (
        <EmptyState
          icon="warning"
          title={text('beauty.loadError')}
          message={text('common.checkConnection')}
          actionLabel={text('common.retry')}
          onAction={() => refetch()}
        />
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon="beauty"
          title={filtered ? text('beauty.emptyFilteredTitle') : text('beauty.emptyTitle')}
          message={filtered ? text('beauty.emptyFilteredMessage') : text('beauty.emptyMessage')}
          actionLabel={filtered ? undefined : text('beauty.emptyAction')}
          onAction={filtered ? undefined : () => navigation.navigate('AddBeauty')}
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
                title={item.productName}
                subtitle={item.brand ?? labels.beautyCategory(item.category)}
                imageUrl={item.imageUrl}
                favorite={item.favorite}
                onPress={() => navigation.navigate('BeautyDetail', { item })}
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
  grid: { paddingHorizontal: spacing.xl, paddingTop: spacing.md, paddingBottom: spacing.xl },
  column: { gap: spacing.md, marginBottom: spacing.lg },
  cell: { width: '30%' },
});
