import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { toApiError } from '../../api/client';
import { AppText, Button, Chip, Header, PhotoField, Screen, TextField } from '../../components/ui';
import { CLOTHING_CATEGORIES, ClothingCategory } from '../../api/types';
import { useAnalyzeItem, useUpdateItem } from '../../features/wardrobe';
import { useT } from '../../i18n';
import { useDomainLabels } from '../../i18n/domain';
import { spacing } from '../../theme';
import type { WardrobeStackParamList } from '../../navigation/types';

function splitList(value: string): string[] {
  return value.split(',').map((s) => s.trim()).filter(Boolean);
}

export function EditItemScreen() {
  const { t: text } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<WardrobeStackParamList>>();
  const { item } = useRoute<RouteProp<WardrobeStackParamList, 'EditItem'>>().params;
  const update = useUpdateItem();
  // Uploading is what /analyze already does; the attributes it also returns are
  // ignored here, because the user is editing their own answers, not asking for
  // new ones. Only the key matters.
  const upload = useAnalyzeItem();
  const [photoUri, setPhotoUri] = useState<string | null>(item.imageUrl ?? null);
  const [imageKey, setImageKey] = useState<string | null>(null);

  // Catalogue values are stored in English and shown as labels, so the fields hold
  // labels while editing and are mapped back on save.
  const [name, setName] = useState(item.name);
  const [category, setCategory] = useState<ClothingCategory>(item.category);
  const [subcategory, setSubcategory] = useState(item.subcategory ?? '');
  const [colors, setColors] = useState(item.colors.map(labels.color).join(', '));
  const [pattern, setPattern] = useState(labels.pattern(item.pattern ?? ''));
  const [styles_, setStyles] = useState(item.styles.map(labels.style).join(', '));
  const [seasons, setSeasons] = useState(item.seasons.map(labels.season).join(', '));
  const [brand, setBrand] = useState(item.brand ?? '');
  const [size, setSize] = useState(item.size ?? '');
  const [error, setError] = useState<string | null>(null);

  const onSave = () => {
    setError(null);
    if (!name.trim()) {
      setError(text('confirm.nameRequired'));
      return;
    }
    update.mutate(
      {
        id: item.id,
        payload: {
          name: name.trim(),
          category,
          subcategory: subcategory.trim(),
          colors: splitList(colors).map(labels.canonical.color),
          pattern: pattern.trim() ? labels.canonical.pattern(pattern) : '',
          styles: splitList(styles_).map(labels.canonical.style),
          seasons: splitList(seasons).map(labels.canonical.season),
          brand: brand.trim(),
          size: size.trim(),
          ...(imageKey ? { imageKey } : {}),
        },
      },
      {
        onSuccess: () => navigation.goBack(),
        onError: (err) => setError(toApiError(err).message),
      },
    );
  };

  return (
    <Screen
      scroll
      footer={
        <Button
          label={text('common.save')}
          onPress={onSave}
          loading={update.isPending}
        />
      }
    >
      <Header
        title={text('item.editTitle')}
        subtitle={text('item.editSubtitle')}
        onBack={() => navigation.goBack()}
      />

      <View style={styles.form}>
        <PhotoField
          uri={photoUri}
          busy={upload.isPending}
          onPick={(asset) => {
            setPhotoUri(asset.uri);
            upload.mutate(asset, {
              onSuccess: (res) => setImageKey(res.imageKey),
              onError: (err) => setError(toApiError(err).message),
            });
          }}
        />

        <TextField
          label={text('confirm.name')}
          value={name}
          onChangeText={setName}
          placeholder={text('confirm.namePlaceholder')}
        />

        <View>
          <AppText variant="label" tone="secondary" style={styles.fieldLabel}>
            {text('common.category')}
          </AppText>
          <View style={styles.chips}>
            {CLOTHING_CATEGORIES.map((c) => (
              <Chip
                key={c}
                label={labels.clothingCategory(c)}
                selected={category === c}
                onPress={() => setCategory(c)}
              />
            ))}
          </View>
        </View>

        <TextField
          label={text('confirm.subcategory')}
          value={subcategory}
          onChangeText={setSubcategory}
          placeholder={text('confirm.subcategoryPlaceholder')}
        />
        <TextField
          label={text('confirm.colors')}
          value={colors}
          onChangeText={setColors}
          placeholder={text('confirm.colorsPlaceholder')}
          hint={text('confirm.commaHint')}
        />
        <TextField
          label={text('confirm.pattern')}
          value={pattern}
          onChangeText={setPattern}
          placeholder={text('confirm.patternPlaceholder')}
        />
        <TextField
          label={text('confirm.styles')}
          value={styles_}
          onChangeText={setStyles}
          placeholder={text('confirm.stylesPlaceholder')}
          hint={text('confirm.commaHint')}
        />
        <TextField
          label={text('confirm.seasons')}
          value={seasons}
          onChangeText={setSeasons}
          placeholder={text('confirm.seasonsPlaceholder')}
          hint={text('confirm.commaHint')}
        />
        <TextField label={text('confirm.brandOptional')} value={brand} onChangeText={setBrand} />
        <TextField
          label={text('confirm.sizeOptional')}
          value={size}
          onChangeText={setSize}
          error={error}
        />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacing.md, marginTop: spacing.lg },
  fieldLabel: { marginBottom: spacing.xs },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
});
