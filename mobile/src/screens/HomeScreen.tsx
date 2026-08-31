import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useRef } from 'react';
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
import { useDomainLabels } from '../i18n/domain';
import { useT } from '../i18n';
import { useRecentItems } from '../features/wardrobe';
import { useSavedLooks } from '../features/outfits';
import { useAuth } from '../store/auth';
import { colors, feedback, radius, spacing, typography } from '../theme';
import { Screen } from '../components/ui';
import { openLookDetail, openOnboarding, openVerifyEmail } from '../navigation/navigationRef';
import { useStylePreferences } from '../features/preferences';
import type { HomeStackParamList } from '../navigation/types';

export function HomeScreen() {
  const { t: text, tPlural } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<HomeStackParamList>>();
  const user = useAuth((s) => s.user);
  const recent = useRecentItems(10);
  const looks = useSavedLooks();
  const prefs = useStylePreferences();
  const onboardingShown = useRef(false);

  // First run: open the style-onboarding once when it hasn't been completed.
  useEffect(() => {
    if (prefs.data && !prefs.data.onboardingCompleted && !onboardingShown.current) {
      onboardingShown.current = true;
      const t = setTimeout(() => openOnboarding(), 400);
      return () => clearTimeout(t);
    }
  }, [prefs.data]);

  const goTab = (tab: string) => navigation.getParent()?.navigate(tab as never);

  return (
    <Screen scroll padded={false}>
      <View style={styles.body}>
        <AppText variant="label" tone="muted">
          {text('home.eyebrow')}
        </AppText>
        <View style={styles.nameRow}>
          <AppText variant="h1">{text('home.welcome')}</AppText>
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
              title={text('home.getReady')}
              subtitle={text('home.getReadyHint')}
              tone="pink"
              onPress={() => goTab('GetReadyTab')}
            />
            <ActionCard
              icon="wardrobe"
              title={text('home.myWardrobe')}
              subtitle={text('home.myWardrobeHint')}
              tone="pink"
              onPress={() => goTab('WardrobeTab')}
            />
          </View>
          <View style={styles.row}>
            <ActionCard
              icon="shop"
              title={text('home.shouldIBuy')}
              subtitle={text('home.shouldIBuyHint')}
              tone="pink"
              onPress={() => navigation.navigate('ShopAssistant')}
            />
            <ActionCard
              icon="beauty"
              title={text('home.beauty')}
              subtitle={text('home.beautyHint')}
              tone="pink"
              onPress={() => goTab('BeautyTab')}
            />
          </View>
        </View>

        <View style={styles.section}>
          <SectionHeader
            title={text('home.recentlyAdded')}
            actionLabel={text('common.seeAll')}
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
                  subtitle={labels.clothingCategory(item.category)}
                  imageUrl={item.imageUrl}
                  favorite={item.favorite}
                />
              )}
            />
          ) : (
            <AddPrompt
              title={text('home.addFirstPiece')}
              subtitle={text('home.addFirstPieceHint')}
              onPress={() => goTab('WardrobeTab')}
            />
          )}
        </View>

        <View style={styles.section}>
          <SectionHeader
            title={text('home.savedLooks')}
            actionLabel={text('home.getReady')}
            onAction={() => goTab('GetReadyTab')}
          />
          {looks.data && looks.data.length > 0 ? (
            <FlatList
              horizontal
              data={looks.data}
              keyExtractor={(l) => l.id}
              showsHorizontalScrollIndicator={false}
              ItemSeparatorComponent={() => <View style={{ width: spacing.md }} />}
              renderItem={({ item }) => (
                <Pressable
                  onPress={() => openLookDetail(item)}
                  android_ripple={{ color: feedback.ripple }}
                  style={({ pressed }) => pressed && styles.lookPressed}
                >
                  <Card style={styles.lookCard}>
                    <AppText variant="title" numberOfLines={1}>
                      {item.title ?? text('home.savedLookFallback')}
                    </AppText>
                    <AppText variant="caption" tone="muted">
                      {tPlural('home.lookPieces', item.items.length)}
                    </AppText>
                  </Card>
                </Pressable>
              )}
            />
          ) : (
            <AddPrompt
              title={text('home.createFirstLook')}
              subtitle={text('home.createFirstLookHint')}
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
  lookPressed: { transform: [{ scale: feedback.pressScaleCard }] },

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
