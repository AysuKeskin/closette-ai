import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useMemo, useState } from 'react';
import { Image, Pressable, StyleSheet, View } from 'react-native';

import { toApiError } from '../../api/client';
import { AppText, Button, Card, Chip, Header, Screen, TextField } from '../../components/ui';
import { CLOTHING_CATEGORIES, ClothingCategory } from '../../api/types';
import { useCreateItem } from '../../features/wardrobe';
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

function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

export function ConfirmItemScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<WardrobeStackParamList>>();
  const route = useRoute<RouteProp<WardrobeStackParamList, 'ConfirmItem'>>();
  const { analysis: response, imageUri } = route.params;
  const ai = response.analysis;
  const createItem = useCreateItem();

  const [name, setName] = useState(ai.subcategory ? titleCase(ai.subcategory) : '');
  const [category, setCategory] = useState<ClothingCategory>(toCategory(ai.category));
  const [subcategory, setSubcategory] = useState(ai.subcategory ?? '');
  const [colors_, setColors] = useState((ai.colors ?? []).join(', '));
  const [pattern, setPattern] = useState(ai.pattern ?? '');
  const [styles_, setStyles] = useState((ai.styles ?? []).join(', '));
  const [seasons, setSeasons] = useState((ai.seasons ?? []).join(', '));
  const [brand, setBrand] = useState('');
  const [size, setSize] = useState('');
  const [error, setError] = useState<string | null>(null);

  const lowConfidence = useMemo(() => ai.confidence > 0 && ai.confidence < 0.75, [ai.confidence]);

  const onSave = () => {
    setError(null);
    if (!name.trim()) {
      setError('Please give this item a name.');
      return;
    }
    createItem.mutate(
      {
        name: name.trim(),
        category,
        subcategory: subcategory.trim() || undefined,
        colors: splitList(colors_),
        pattern: pattern.trim() || undefined,
        styles: splitList(styles_),
        seasons: splitList(seasons),
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
      footer={<Button label="Save to wardrobe" icon="♡" onPress={onSave} loading={createItem.isPending} />}
    >
      <Header title="Confirm details" subtitle="Edit anything before saving" onBack={() => navigation.goBack()} />

      {imageUri ? (
        <Image source={{ uri: imageUri }} style={styles.image} resizeMode="cover" />
      ) : null}

      {lowConfidence ? (
        <Card style={styles.confidence}>
          <AppText variant="label" tone="brand">
            ✨ We think this is a {ai.subcategory || 'item'}. Is that right? Feel free to fix anything.
          </AppText>
        </Card>
      ) : null}

      {ai.color_details && ai.color_details.length > 0 ? (
        <View style={styles.colorsBlock}>
          <AppText variant="label" tone="secondary" style={styles.fieldLabel}>
            Detected colours
          </AppText>
          <View style={styles.swatches}>
            {ai.color_details.map((c) => (
              <View key={`${c.hex}-${c.name}`} style={styles.swatch}>
                <View style={[styles.swatchDot, { backgroundColor: c.hex }]} />
                <AppText variant="caption">{`${c.name} · ${c.percentage}%`}</AppText>
              </View>
            ))}
          </View>
        </View>
      ) : null}

      <View style={styles.form}>
        <TextField label="Name" value={name} onChangeText={setName} placeholder="e.g. Black mini dress" />

        <View>
          <AppText variant="label" tone="secondary" style={styles.fieldLabel}>
            Category
          </AppText>
          <View style={styles.chips}>
            {CLOTHING_CATEGORIES.map((c) => (
              <Chip key={c} label={titleCase(c)} selected={category === c} onPress={() => setCategory(c)} />
            ))}
          </View>
        </View>

        <TextField label="Subcategory" value={subcategory} onChangeText={setSubcategory} placeholder="e.g. mini dress" />
        <TextField label="Colors" value={colors_} onChangeText={setColors} placeholder="black, cream" hint="Separate with commas" />
        <TextField label="Pattern" value={pattern} onChangeText={setPattern} placeholder="solid" />
        <TextField label="Styles" value={styles_} onChangeText={setStyles} placeholder="minimal, elegant" hint="Separate with commas" />
        <TextField label="Seasons" value={seasons} onChangeText={setSeasons} placeholder="spring, summer" hint="Separate with commas" />
        <TextField label="Brand (optional)" value={brand} onChangeText={setBrand} />
        <TextField label="Size (optional)" value={size} onChangeText={setSize} error={error} />
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
