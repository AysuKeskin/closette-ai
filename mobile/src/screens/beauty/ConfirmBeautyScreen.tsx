import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import * as ImagePicker from 'expo-image-picker';
import { useMemo, useState } from 'react';
import { Image, StyleSheet, View } from 'react-native';

import { toApiError } from '../../api/client';
import { AppText, Button, Card, Chip, Header, Screen, TextField } from '../../components/ui';
import { BEAUTY_CATEGORIES, BeautyCategory } from '../../api/types';
import { useCreateBeauty, useScanIngredients } from '../../features/beauty';
import { useT } from '../../i18n';
import { colors, radius, spacing } from '../../theme';
import type { BeautyStackParamList } from '../../navigation/types';

function toCategory(raw: string): BeautyCategory {
  const upper = (raw ?? '').trim().toUpperCase();
  return (BEAUTY_CATEGORIES as readonly string[]).includes(upper)
    ? (upper as BeautyCategory)
    : 'SKINCARE';
}

function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

export function ConfirmBeautyScreen() {
  const { t: text } = useT();
  const navigation = useNavigation<NativeStackNavigationProp<BeautyStackParamList>>();
  const route = useRoute<RouteProp<BeautyStackParamList, 'ConfirmBeauty'>>();
  const { analysis: response, candidate, imageUri } = route.params;
  const ai = response?.analysis;
  const createBeauty = useCreateBeauty();

  const [productName, setProductName] = useState(
    candidate?.productName ?? (ai?.productName ? titleCase(ai.productName) : ''),
  );
  const [brand, setBrand] = useState(candidate?.brand ?? ai?.brand ?? '');
  const [category, setCategory] = useState<BeautyCategory>(
    candidate?.category ?? toCategory(ai?.category ?? 'skincare'),
  );
  const [size, setSize] = useState('');
  const [ingredients, setIngredients] = useState((candidate?.ingredients ?? []).join(', '));
  const [error, setError] = useState<string | null>(null);
  const [scanNote, setScanNote] = useState<string | null>(null);
  const scan = useScanIngredients();

  const scanIngredients = async (mode: 'camera' | 'library') => {
    setError(null);
    setScanNote(null);
    const perm =
      mode === 'camera'
        ? await ImagePicker.requestCameraPermissionsAsync()
        : await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) {
      setError(text(mode === 'camera' ? 'item.permissionCamera' : 'item.permissionPhotos'));
      return;
    }
    const picked =
      mode === 'camera'
        ? await ImagePicker.launchCameraAsync({ quality: 0.7 })
        : await ImagePicker.launchImageLibraryAsync({ quality: 0.7, mediaTypes: ['images'] });
    if (picked.canceled || !picked.assets?.[0]) return;
    const asset = picked.assets[0];
    scan.mutate(
      { uri: asset.uri, mimeType: asset.mimeType ?? 'image/jpeg', name: asset.fileName ?? 'ingredients.jpg' },
      {
        onSuccess: (list) => {
          if (list.length === 0) {
            setScanNote(text('beautyForm.noIngredientsDetected'));
            return;
          }
          const merged = Array.from(new Set([...splitList(ingredients), ...list]));
          setIngredients(merged.join(', '));
          setScanNote(`Added ${list.length} ingredient${list.length === 1 ? '' : 's'} from the photo.`);
        },
        onError: (err) => setError(toApiError(err).message),
      },
    );
  };

  const photoUri = imageUri ?? candidate?.imageUrl ?? null;
  const lowConfidence = useMemo(
    () => !!ai && ai.confidence > 0 && ai.confidence < 0.75,
    [ai],
  );

  const onSave = () => {
    setError(null);
    if (!productName.trim()) {
      setError(text('beautyForm.nameRequired'));
      return;
    }
    createBeauty.mutate(
      {
        productName: productName.trim(),
        brand: brand.trim() || undefined,
        category,
        size: size.trim() || undefined,
        ingredients: splitList(ingredients),
        imageKey: response?.imageKey || undefined,
        // Search-sourced products carry an external image URL instead of a MinIO upload.
        imageUrl: response?.imageKey ? undefined : candidate?.imageUrl ?? undefined,
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
      footer={<Button label={text('beautyForm.saveToShelf')} iconName="beauty" onPress={onSave} loading={createBeauty.isPending} />}
    >
      <Header title={text('beautyForm.confirmTitle')} subtitle={text('beautyForm.confirmSubtitle')} onBack={() => navigation.goBack()} />

      {photoUri ? (
        <Image source={{ uri: photoUri }} style={styles.image} resizeMode="cover" />
      ) : null}

      {lowConfidence ? (
        <Card style={styles.confidence}>
          <AppText variant="label" tone="brand">
            We think this is a {ai?.productName || 'product'}. Is that right? Feel free to fix anything.
          </AppText>
        </Card>
      ) : null}

      <View style={styles.form}>
        <TextField label={text('beautyForm.productName')} value={productName} onChangeText={setProductName} placeholder={text('beautyForm.productNamePlaceholder')} />
        <TextField label={text('beautyForm.brandOptional')} value={brand} onChangeText={setBrand} placeholder={text('beautyForm.brandPlaceholderOrdinary')} />

        <View>
          <AppText variant="label" tone="secondary" style={styles.fieldLabel}>
            {text('common.category')}
          </AppText>
          <View style={styles.chips}>
            {BEAUTY_CATEGORIES.map((c) => (
              <Chip key={c} label={titleCase(c)} selected={category === c} onPress={() => setCategory(c)} />
            ))}
          </View>
        </View>

        <TextField label={text('beautyForm.sizeOptional')} value={size} onChangeText={setSize} placeholder={text('beautyForm.sizePlaceholder')} />
        <View>
          <TextField
            label={text('beautyForm.ingredientsOptional')}
            value={ingredients}
            onChangeText={setIngredients}
            placeholder={text('beautyForm.ingredientsPlaceholder')}
            hint={text('beautyForm.ingredientsHint')}
            error={error}
            multiline
          />
          <View style={styles.scanRow}>
            <Button label={text('beautyForm.scanFromPhoto')} iconName="camera" variant="secondary" size="sm" fullWidth={false} onPress={() => scanIngredients('camera')} loading={scan.isPending} style={styles.scanBtn} />
            <Button label={text('beautyForm.fromLibrary')} iconName="gallery" variant="ghost" size="sm" fullWidth={false} onPress={() => scanIngredients('library')} style={styles.scanBtn} />
          </View>
          {scanNote ? (
            <AppText variant="caption" tone="muted" style={styles.scanNote}>
              {scanNote}
            </AppText>
          ) : null}
        </View>
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
  form: { gap: spacing.lg },
  fieldLabel: { marginLeft: spacing.xs, marginBottom: spacing.sm },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  scanRow: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.sm },
  scanBtn: { flex: 1 },
  scanNote: { marginTop: spacing.sm, marginLeft: spacing.xs },
});
