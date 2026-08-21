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
import { spacing } from '../../theme';
import type { WardrobeStackParamList } from '../../navigation/types';

export function WardrobeListScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<WardrobeStackParamList>>();
  const [category, setCategory] = useState<ClothingCategory | undefined>(undefined);
  const [search, setSearch] = useState('');

  const { data, isLoading, isError, refetch, isRefetching } = useWardrobe({
    category,
    q: search.trim() || undefined,
  });

  return (
    <Screen padded={false}>
      <View style={styles.top}>
        <Header
          title="Wardrobe"
          subtitle="Everything you own, in one place"
          right={<Button label="Add" icon="+" fullWidth={false} onPress={() => navigation.navigate('AddItem')} />}
        />
        <TextField
          placeholder="Search e.g. black skirt"
          value={search}
          onChangeText={setSearch}
          returnKeyType="search"
        />
        <View style={styles.filters}>
          <FlatList
            horizontal
            data={['ALL', ...CLOTHING_CATEGORIES]}
            keyExtractor={(c) => c}
            showsHorizontalScrollIndicator={false}
            ItemSeparatorComponent={() => <View style={{ width: spacing.sm }} />}
            renderItem={({ item }) => (
              <Chip
                label={item === 'ALL' ? 'All' : titleCase(item)}
                selected={item === 'ALL' ? !category : category === item}
                onPress={() => setCategory(item === 'ALL' ? undefined : (item as ClothingCategory))}
              />
            )}
          />
        </View>
      </View>

      {isLoading ? (
        <LoadingState message="Loading your wardrobe…" />
      ) : isError ? (
        <EmptyState
          icon="warning"
          title="Couldn't load your wardrobe"
          message="Check your connection and try again."
          actionLabel="Retry"
          onAction={() => refetch()}
        />
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon="wardrobe"
          title="Your wardrobe is empty"
          message="Add your first piece — snap a photo and we'll fill in the details for you."
          actionLabel="+ Add an item"
          onAction={() => navigation.navigate('AddItem')}
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
                subtitle={titleCase(item.category)}
                imageUrl={item.imageUrl}
                favorite={item.favorite}
                onPress={() => navigation.navigate('ItemDetail', { item })}
              />
            </View>
          )}
          ListFooterComponent={
            data.length > 0 ? (
              <AppText variant="caption" tone="muted" center style={styles.count}>
                {data.length} item{data.length === 1 ? '' : 's'}
              </AppText>
            ) : null
          }
        />
      )}
    </Screen>
  );
}

function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

const styles = StyleSheet.create({
  top: { paddingHorizontal: spacing.xl, gap: spacing.md },
  filters: { marginTop: spacing.xs, marginBottom: spacing.sm },
  grid: { paddingHorizontal: spacing.xl, paddingTop: spacing.md, paddingBottom: spacing.xl },
  column: { gap: spacing.md, marginBottom: spacing.lg },
  cell: { width: '30%' },
  count: { marginTop: spacing.lg },
});
