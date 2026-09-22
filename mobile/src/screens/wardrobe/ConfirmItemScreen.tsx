import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useMemo, useState } from 'react';
import { Image, Pressable, StyleSheet, View } from 'react-native';

import { toApiError } from '../../api/client';
import { AppText, Button, Card, Chip, Header, Screen, TextField } from '../../components/ui';
import { CLOTHING_CATEGORIES, ClothingCategory } from '../../api/types';
import { useCreateItem } from '../../features/wardrobe';
import { useT } from '../../i18n';
import { useDomainLabels } from '../../i18n/domain';
import { colors, radius, spacing } from '../../theme';
import type { WardrobeStackParamList } from '../../navigation/types';

const CATEGORY_MAP: Record<string, ClothingCategory> = {
  dress: 'DRESSES',
  dresses: 'DRESSES',
  top: 'TOPS',
  tops: 'TOPS',
  bottom: 'BOTTOMS',
  bottoms: 'BOTTOMS',
  skirt: 'BOTTOMS',
  shoes: 'SHOES',
  shoe: 'SHOES',
  bag: 'BAGS',
  bags: 'BAGS',
  outerwear: 'OUTERWEAR',
  coat: 'OUTERWEAR',
  jewelry: 'JEWELRY',
  accessory: 'ACCESSORIES',
  accessories: 'ACCESSORIES',
};

function toCategory(raw: string): ClothingCategory {
  return CATEGORY_MAP[raw?.toLowerCase()] ?? 'TOPS';
}

function titleCase(value: string, language: string): string {
  // The subcategory arrives in the user's language now: "ipek bluz" must
  // capitalise to "İpek bluz", not "Ipek bluz".
  const locale = language === 'tr' ? 'tr-TR' : 'en-US';
  return value.charAt(0).toLocaleUpperCase(locale) + value.slice(1).toLocaleLowerCase(locale);
}

export function ConfirmItemScreen() {
  const { t: text, language } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<WardrobeStackParamList>>();
  const route = useRoute<RouteProp<WardrobeStackParamList, 'ConfirmItem'>>();
  const { analysis: response, imageUri } = route.params;
  const ai = response.analysis;
  const createItem = useCreateItem();

  const [name, setName] = useState(ai.subcategory ? titleCase(ai.subcategory, language) : '');
  const [category, setCategory] = useState<ClothingCategory>(toCategory(ai.category));
  const [subcategory, setSubcategory] = useState(ai.subcategory ?? '');
  const [colors_, setColors] = useState((ai.colors ?? []).map(labels.color).join(', '));
  const [pattern, setPattern] = useState(labels.pattern(ai.pattern ?? ''));
  const [styles_, setStyles] = useState((ai.styles ?? []).map(labels.style).join(', '));
  const [seasons, setSeasons] = useState((ai.seasons ?? []).map(labels.season).join(', '));
  const [brand, setBrand] = useState('');
  const [size, setSize] = useState('');
  const [error, setError] = useState<string | null>(null);

  // What the colour pipeline measured, kept only while the user leaves the colours
  // as they came. Once they type their own, a share taken from the photo would be
  // attached to a colour nobody measured.
  const measuredShares = useMemo(() => {
    const details = ai.color_details ?? [];
    if (!details.length) return undefined;
    const asMeasured = details.map((d) => d.name).join(', ');
    const asShown = details.map((d) => labels.color(d.name)).join(', ');
    if (colors_.trim() !== asShown && colors_.trim() !== asMeasured) return undefined;
    return details.map((d) => `${d.name}:${d.percentage}`);
  }, [ai.color_details, colors_, labels]);

  const lowConfidence = useMemo(() => ai.confidence > 0 && ai.confidence < 0.75, [ai.confidence]);

  const onSave = () => {
    setError(null);
    if (!name.trim()) {
      setError(text('confirm.nameRequired'));
      return;
    }
    createItem.mutate(
      {
        name: name.trim(),
        category,
        subcategory: subcategory.trim() || undefined,
        colors: splitList(colors_).map(labels.canonical.color),
        colorShares: measuredShares,
        pattern: pattern.trim() ? labels.canonical.pattern(pattern) : undefined,
        styles: splitList(styles_).map(labels.canonical.style),
        seasons: splitList(seasons).map(labels.canonical.season),
        brand: brand.trim() || undefined,
        size: size.trim() || undefined,
        imageKey: response.imageKey || undefined,
      },
      {
        onSuccess: () => navigation.popToTop(),
        onError: (err) => setError(toApiError(err).message),
      },
    );
  };

  return (
    <Screen
      scroll
      footer={<Button label={text('confirm.saveToWardrobe')} icon="♡" onPress={onSave} loading={createItem.isPending} />}
    >
      <Header title={text('confirm.title')} subtitle={text('confirm.subtitle')} onBack={() => navigation.goBack()} />

      {imageUri ? (
        <Image source={{ uri: imageUri }} style={styles.image} resizeMode="cover" />
      ) : null}

      {lowConfidence ? (
        <Card style={styles.confidence}>
          <AppText variant="label" tone="brand">
            {text('confirm.lowConfidence', { noun: ai.subcategory || text('confirm.thisItem') })}
          </AppText>
        </Card>
      ) : null}

      {ai.color_details && ai.color_details.length > 0 ? (
        <View style={styles.colorsBlock}>
          <AppText variant="label" tone="secondary" style={styles.fieldLabel}>
            {text('confirm.detectedColors')}
          </AppText>
          <View style={styles.swatches}>
            {ai.color_details.map((c) => (
              <View key={`${c.hex}-${c.name}`} style={styles.swatch}>
                <View style={[styles.swatchDot, { backgroundColor: c.hex }]} />
                <AppText variant="caption">{`${labels.color(c.name)} · ${c.percentage}%`}</AppText>
              </View>
            ))}
          </View>
        </View>
      ) : null}

      <View style={styles.form}>
        <TextField label={text('confirm.name')} value={name} onChangeText={setName} placeholder={text('confirm.namePlaceholder')} />

        <View>
          <AppText variant="label" tone="secondary" style={styles.fieldLabel}>
            {text('common.category')}
          </AppText>
          <View style={styles.chips}>
            {CLOTHING_CATEGORIES.map((c) => (
              <Chip key={c} label={labels.clothingCategory(c)} selected={category === c} onPress={() => setCategory(c)} />
            ))}
          </View>
        </View>

        <TextField label={text('confirm.subcategory')} value={subcategory} onChangeText={setSubcategory} placeholder={text('confirm.subcategoryPlaceholder')} />
        <TextField label={text('confirm.colors')} value={colors_} onChangeText={setColors} placeholder={text('confirm.colorsPlaceholder')} hint={text('confirm.commaHint')} />
        <TextField label={text('confirm.pattern')} value={pattern} onChangeText={setPattern} placeholder={text('confirm.patternPlaceholder')} />
        <TextField label={text('confirm.styles')} value={styles_} onChangeText={setStyles} placeholder={text('confirm.stylesPlaceholder')} hint={text('confirm.commaHint')} />
        <TextField label={text('confirm.seasons')} value={seasons} onChangeText={setSeasons} placeholder={text('confirm.seasonsPlaceholder')} hint={text('confirm.commaHint')} />
        <TextField label={text('confirm.brandOptional')} value={brand} onChangeText={setBrand} />
        <TextField label={text('confirm.sizeOptional')} value={size} onChangeText={setSize} error={error} />
      </View>
    </Screen>
  );
}

function splitList(value: string): string[] {
  return value
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

const styles = StyleSheet.create({
  image: {
    width: '100%',
    height: 240,
    borderRadius: radius.lg,
    backgroundColor: colors.surfaceAlt,
    marginBottom: spacing.lg,
  },
  confidence: { backgroundColor: colors.surfaceAlt, marginBottom: spacing.lg },
  colorsBlock: { marginBottom: spacing.lg },
  swatches: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  swatch: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.md,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  swatchDot: {
    width: 16,
    height: 16,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
  },
  form: { gap: spacing.lg },
  fieldLabel: { marginLeft: spacing.xs, marginBottom: spacing.sm },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
});
