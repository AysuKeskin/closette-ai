import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import { AppText, Card, EmptyState, Header, ItemTile, LoadingState, Screen } from '../../components/ui';
import { useSavedLooks } from '../../features/outfits';
import { useT } from '../../i18n';
import { colors, feedback, spacing } from '../../theme';
import type { AppStackParamList } from '../../navigation/types';

export function SavedLooksScreen() {
  const { t: text } = useT();
  const navigation = useNavigation<NativeStackNavigationProp<AppStackParamList>>();
  const looks = useSavedLooks();

  return (
    <Screen scroll padded={false}>
      <View style={styles.top}>
        <Header
          title={text('looks.savedTitle')}
          subtitle={text('looks.savedSubtitle')}
          onBack={() => navigation.goBack()}
        />
      </View>

      {looks.isLoading ? (
        <LoadingState message={text('looks.loading')} />
      ) : !looks.data || looks.data.length === 0 ? (
        <EmptyState
          icon="getready"
          title={text('looks.emptyTitle')}
          message={text('looks.emptyMessage')}
        />
      ) : (
        <View style={styles.list}>
          {looks.data.map((look) => (
            <Pressable
              key={look.id}
              onPress={() => navigation.navigate('LookDetail', { look })}
              android_ripple={{ color: feedback.ripple }}
              style={({ pressed }) => pressed && styles.pressed}
            >
              <Card style={styles.look}>
                <View style={styles.lookHead}>
                  <AppText variant="title" style={styles.lookTitle}>
                    {look.title ?? text('looks.fallbackTitle')}
                  </AppText>
                  <AppText style={styles.chevron}>›</AppText>
                </View>
                {look.occasion ? (
                  <AppText variant="caption" tone="muted">
                    {look.occasion}
                  </AppText>
                ) : null}
                {look.items.length > 0 ? (
                  <FlatList
                    horizontal
                    data={look.items}
                    keyExtractor={(i) => i.id}
                    scrollEnabled={false}
                    showsHorizontalScrollIndicator={false}
                    ItemSeparatorComponent={() => <View style={{ width: spacing.md }} />}
                    renderItem={({ item }) => (
                      <ItemTile width={96} title={item.name} subtitle={item.category.toLowerCase()} imageUrl={item.imageUrl} />
                    )}
                    style={styles.items}
                  />
                ) : null}
              </Card>
            </Pressable>
          ))}
        </View>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  top: { paddingHorizontal: spacing.xl },
  list: { paddingHorizontal: spacing.xl, gap: spacing.lg, paddingBottom: spacing.xl },
  look: { gap: spacing.sm },
  lookHead: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  lookTitle: { flex: 1 },
  chevron: { fontSize: 22, color: colors.textMuted },
  items: { marginTop: spacing.sm },
  pressed: { transform: [{ scale: feedback.pressScaleCard }] },
});
