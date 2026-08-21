import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { Alert, FlatList, StyleSheet, View } from 'react-native';

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
import { BEAUTY_CATEGORIES } from '../../api/types';
import { useBeauty, useDeleteBeauty } from '../../features/beauty';
import { spacing } from '../../theme';
import type { BeautyStackParamList } from '../../navigation/types';

function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

export function BeautyScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<BeautyStackParamList>>();
  const [category, setCategory] = useState<string | undefined>(undefined);
  const { data, isLoading, isError, refetch, isRefetching } = useBeauty(category);
  const deleteBeauty = useDeleteBeauty();

  const confirmDelete = (id: string, name: string) => {
    Alert.alert(
      'Delete product?',
      `"${name}" will be removed from your shelf. This can't be undone.`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Delete', style: 'destructive', onPress: () => deleteBeauty.mutate(id) },
      ],
    );
  };

  return (
    <Screen padded={false}>
      <View style={styles.top}>
        <Header
          title="Beauty"
          subtitle="Skincare, makeup & more"
          right={<Button label="Add" icon="+" fullWidth={false} onPress={() => navigation.navigate('AddBeauty')} />}
        />
        <FlatList
          horizontal
          data={['ALL', ...BEAUTY_CATEGORIES]}
          keyExtractor={(c) => c}
          showsHorizontalScrollIndicator={false}
          ItemSeparatorComponent={() => <View style={{ width: spacing.sm }} />}
          renderItem={({ item }) => (
            <Chip
              label={item === 'ALL' ? 'All' : titleCase(item)}
              selected={item === 'ALL' ? !category : category === item}
              onPress={() => setCategory(item === 'ALL' ? undefined : item)}
            />
          )}
        />
      </View>

      {isLoading ? (
        <LoadingState message="Loading your beauty shelf…" />
      ) : isError ? (
        <EmptyState
          icon="warning"
          title="Couldn't load your beauty items"
          actionLabel="Retry"
          onAction={() => refetch()}
        />
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon="beauty"
          title="Your beauty shelf is empty"
          message="Add your first product — we’ll track its ingredients and expiry for you."
          actionLabel="＋ Add a product"
          onAction={() => navigation.navigate('AddBeauty')}
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
                subtitle={item.brand ?? titleCase(item.category)}
                imageUrl={item.imageUrl}
                favorite={item.favorite}
                onDelete={() => confirmDelete(item.id, item.productName)}
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
