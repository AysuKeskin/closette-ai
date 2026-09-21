import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Alert, Dimensions, StyleSheet, View } from 'react-native';

import { AppText, Button, Header, ItemTile, Screen } from '../../components/ui';
import { useDeleteLook } from '../../features/outfits';
import { useDomainLabels } from '../../i18n/domain';
import { openItemDetail } from '../../navigation/navigationRef';
import { useT } from '../../i18n';
import { colors, radius, spacing } from '../../theme';
import type { AppStackParamList } from '../../navigation/types';

// Three tiles per row, sized off the screen width minus the page padding and inter-tile gaps.
const COLS = 3;
const TILE_W = Math.floor((Dimensions.get('window').width - spacing.xl * 2 - spacing.md * (COLS - 1)) / COLS);

function formatDate(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString();
}

/** Full view of one saved look, with its pieces and a remove (un-save) action. */
export function LookDetailScreen() {
  const { t: text, tPlural } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<AppStackParamList>>();
  const look = useRoute<RouteProp<AppStackParamList, 'LookDetail'>>().params.look;
  const deleteLook = useDeleteLook();

  const remove = () => {
    Alert.alert(text('looks.removeTitle'), text('looks.removeBody'), [
      { text: text('common.cancel'), style: 'cancel' },
      {
        text: text('looks.remove'),
        style: 'destructive',
        onPress: () => deleteLook.mutate(look.id, { onSuccess: () => navigation.goBack() }),
      },
    ]);
  };

  const created = formatDate(look.createdAt);

  return (
    <Screen
      scroll
      footer={
        <Button
          label={text('looks.removeFromSaved')}
          variant="ghost"
          onPress={remove}
          loading={deleteLook.isPending}
        />
      }
    >
      <Header title={look.title ?? text('looks.fallbackTitle')} onBack={() => navigation.goBack()} stackedBack />

      <View style={styles.meta}>
        {look.occasion ? <AppText variant="body" tone="secondary">{look.occasion}</AppText> : null}
        {created ? (
          <AppText variant="caption" tone="muted">
            {text('looks.savedOn', { date: created })}
          </AppText>
        ) : null}
      </View>

      {look.rationale ? (
        <View style={styles.rationaleCard}>
          <AppText variant="body" style={styles.rationaleText}>
            {look.rationale}
          </AppText>
        </View>
      ) : null}

      <AppText variant="label" tone="muted" style={styles.sectionLabel}>
        {tPlural('home.lookPieces', look.items.length)}
      </AppText>
      <View style={styles.grid}>
        {look.items.map((item) => (
          <ItemTile
            key={item.id}
            width={TILE_W}
            title={item.name}
            subtitle={labels.clothingCategory(item.category)}
            imageUrl={item.imageUrl}
            onPress={() => openItemDetail(item)}
          />
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  meta: { gap: spacing.xs, marginBottom: spacing.md },
  rationaleCard: {
    backgroundColor: colors.surfaceAlt,
    borderRadius: radius.lg,
    padding: spacing.lg,
  },
  rationaleText: { lineHeight: 22 },
  sectionLabel: { marginTop: spacing.xl, marginBottom: spacing.md, letterSpacing: 1 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
});
