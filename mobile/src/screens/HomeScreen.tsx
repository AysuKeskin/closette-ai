import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import {
  ActionCard,
  AppText,
  Card,
  Icon,
  ItemTile,
  SectionHeader,
  VerifyBanner,
} from '../components/ui';
import { useRecentItems } from '../features/wardrobe';
import { useSavedLooks } from '../features/outfits';
import { useAuth } from '../store/auth';
import { colors, feedback, radius, spacing, typography } from '../theme';
import { Screen } from '../components/ui';
import { openVerifyEmail } from '../navigation/navigationRef';
import type { HomeStackParamList } from '../navigation/types';

export function HomeScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<HomeStackParamList>>();
  const user = useAuth((s) => s.user);
  const recent = useRecentItems(10);
  const looks = useSavedLooks();

  const goTab = (tab: string) => navigation.getParent()?.navigate(tab as never);

  return (
    <Screen scroll padded={false}>
      <View style={styles.body}>
        <AppText variant="label" tone="muted">
          {greeting()}
        </AppText>
        <View style={styles.nameRow}>
          <AppText variant="h1">Welcome</AppText>
          <Icon name="ai-magic" size={26} />
        </View>

        {user && !user.emailVerified ? (
          <View style={styles.banner}>
            <VerifyBanner onPress={openVerifyEmail} />
          </View>
        ) : null}

        <View style={styles.grid}>
          <View style={styles.row}>
            <ActionCard
              icon="getready"
              title="Get Ready"
              subtitle="What should I wear?"
              tone="pink"
              onPress={() => goTab('GetReadyTab')}
            />
            <ActionCard
              icon="wardrobe"
              title="My Stuff"
              subtitle="View your collection"
              tone="pink"
              onPress={() => goTab('WardrobeTab')}
            />
          </View>
          <View style={styles.row}>
            <ActionCard
              icon="shop"
              title="Should I Buy This?"
              subtitle="Check before you shop"
              tone="pink"
              onPress={() => navigation.navigate('ShopAssistant')}
            />
            <ActionCard
              icon="beauty"
              title="Beauty"
              subtitle="Your beauty collection"
              tone="pink"
              onPress={() => goTab('BeautyTab')}
            />
          </View>
        </View>

        <View style={styles.section}>
          <SectionHeader
            title="Recently added"
            actionLabel="See all"
            onAction={() => goTab('WardrobeTab')}
          />
          {recent.data && recent.data.length > 0 ? (
            <FlatList
              horizontal
              data={recent.data}
              keyExtractor={(i) => i.id}
              showsHorizontalScrollIndicator={false}
              ItemSeparatorComponent={() => <View style={{ width: spacing.md }} />}
              renderItem={({ item }) => (
                <ItemTile
                  width={124}
                  title={item.name}
                  subtitle={item.category.toLowerCase()}
                  imageUrl={item.imageUrl}
                  favorite={item.favorite}
                />
              )}
            />
          ) : (
            <AddPrompt
              title="Add your first piece"
              subtitle="Snap a photo — we’ll fill in the details"
              onPress={() => goTab('WardrobeTab')}
            />
          )}
        </View>

        <View style={styles.section}>
          <SectionHeader title="Saved looks" actionLabel="Get Ready" onAction={() => goTab('GetReadyTab')} />
          {looks.data && looks.data.length > 0 ? (
            <FlatList
              horizontal
              data={looks.data}
              keyExtractor={(l) => l.id}
              showsHorizontalScrollIndicator={false}
              ItemSeparatorComponent={() => <View style={{ width: spacing.md }} />}
              renderItem={({ item }) => (
                <Card style={styles.lookCard}>
                  <AppText variant="title" numberOfLines={1}>
                    {item.title ?? 'Saved look'}
                  </AppText>
                  <AppText variant="caption" tone="muted">
                    {item.items.length} pieces
                  </AppText>
                </Card>
              )}
            />
          ) : (
            <AddPrompt
              title="Create your first look"
              subtitle="Tell us the occasion — we’ll style it"
              onPress={() => goTab('GetReadyTab')}
            />
          )}
        </View>
      </View>
    </Screen>
  );
}

/** Tappable "add now" card that replaces passive empty-state text. */
function AddPrompt({
  title,
  subtitle,
  onPress,
}: {
  title: string;
  subtitle: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={title}
      android_ripple={{ color: feedback.ripple }}
      style={({ pressed }) => [styles.addCard, pressed && styles.addPressed]}
    >
      <View style={styles.plusBadge}>
        <AppText style={styles.plus}>＋</AppText>
      </View>
      <View style={styles.addText}>
        <AppText style={styles.addTitle}>{title}</AppText>
        <AppText variant="caption" tone="muted">
          {subtitle}
        </AppText>
      </View>
      <AppText style={styles.addChevron}>›</AppText>
    </Pressable>
  );
}

function greeting(): string {
  return 'YOUR CLOSET';
}

const styles = StyleSheet.create({
  body: { paddingHorizontal: spacing.xl, paddingTop: spacing.xxl },
  nameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    marginTop: spacing.xs,
    marginBottom: spacing.lg,
  },
  banner: { marginBottom: spacing.lg },
  grid: { gap: spacing.md },
  row: { flexDirection: 'row', gap: spacing.md },
  section: { marginTop: spacing.xxl },
  lookCard: { width: 180, gap: spacing.xs },

  addCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    borderWidth: 1.5,
    borderColor: colors.primary,
    borderStyle: 'dashed',
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
  },
  addPressed: { backgroundColor: colors.surfaceAlt, transform: [{ scale: feedback.pressScaleCard }] },
  plusBadge: {
    width: 32,
    height: 32,
    borderRadius: radius.pill,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  plus: { color: colors.onPrimary, fontSize: 18, fontWeight: typography.weight.bold, lineHeight: 21 },
  addText: { flex: 1, gap: 1 },
  addTitle: { fontSize: typography.size.label, fontWeight: typography.weight.semibold, color: colors.textPrimary },
  addChevron: { fontSize: 20, color: colors.primary },
});
