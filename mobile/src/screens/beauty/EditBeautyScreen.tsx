import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import * as ImagePicker from 'expo-image-picker';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { toApiError } from '../../api/client';
import { AppText, Button, Card, Chip, Header, Screen, TextField } from '../../components/ui';
import { BEAUTY_CATEGORIES, BeautyCategory } from '../../api/types';
import { useScanIngredients, useUpdateBeauty } from '../../features/beauty';
import { useT } from '../../i18n';
import { useDomainLabels } from '../../i18n/domain';
import { spacing } from '../../theme';
import type { BeautyStackParamList } from '../../navigation/types';


function splitList(value: string): string[] {
  return value.split(',').map((s) => s.trim()).filter(Boolean);
}

export function EditBeautyScreen() {
  const { t: text } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<BeautyStackParamList>>();
  const item = useRoute<RouteProp<BeautyStackParamList, 'EditBeauty'>>().params.item;
  const update = useUpdateBeauty();
  const scan = useScanIngredients();

  const [productName, setProductName] = useState(item.productName);
  const [brand, setBrand] = useState(item.brand ?? '');
  const [category, setCategory] = useState<BeautyCategory>(item.category);
  const [size, setSize] = useState(item.size ?? '');
  const [ingredients, setIngredients] = useState((item.ingredients ?? []).join(', '));
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);

  const runScan = async (mode: 'camera' | 'library') => {
    setError(null);
    setNote(null);
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
            setNote(text('beautyForm.noIngredientsDetected'));
            return;
          }
          const merged = Array.from(new Set([...splitList(ingredients), ...list]));
          setIngredients(merged.join(', '));
          setNote(`Added ${list.length} ingredient${list.length === 1 ? '' : 's'} from the photo.`);
        },
        onError: (err) => setError(toApiError(err).message),
      },
    );
  };

  const onSave = () => {
    setError(null);
    if (!productName.trim()) {
      setError(text('beautyForm.nameRequired'));
      return;
    }
    update.mutate(
      {
        id: item.id,
        payload: {
          productName: productName.trim(),
          brand: brand.trim(),
          category,
          size: size.trim(),
          ingredients: splitList(ingredients),
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
      footer={<Button label={text('beautyForm.saveChanges')} iconName="beauty" onPress={onSave} loading={update.isPending} />}
    >
      <Header title={text('beautyForm.editTitle')} subtitle={text('beautyForm.editSubtitle')} onBack={() => navigation.goBack()} stackedBack />

      <View style={styles.form}>
        <TextField label={text('beautyForm.productName')} value={productName} onChangeText={setProductName} />
        <TextField label={text('beautyForm.brand')} value={brand} onChangeText={setBrand} placeholder={text('beautyForm.brandPlaceholderCerave')} />

        <View>
          <AppText variant="label" tone="secondary" style={styles.label}>
            {text('common.category')}
          </AppText>
          <View style={styles.chips}>
            {BEAUTY_CATEGORIES.map((c) => (
              <Chip key={c} label={labels.beautyCategory(c)} selected={category === c} onPress={() => setCategory(c)} />
            ))}
          </View>
        </View>

        <TextField label={text('beautyForm.size')} value={size} onChangeText={setSize} placeholder={text('beautyForm.sizePlaceholderMl')} />

        <View>
          <AppText variant="label" tone="secondary" style={styles.label}>
            {text('beautyForm.ingredients')}
          </AppText>
          <TextField
            value={ingredients}
            onChangeText={setIngredients}
            multiline
            placeholder={text('beautyForm.ingredientsHintEdit')}
            style={styles.ingredients}
          />
          <View style={styles.scanRow}>
            <Button label={text('beautyForm.scanFromPhoto')} iconName="camera" variant="secondary" size="sm" fullWidth={false} onPress={() => runScan('camera')} loading={scan.isPending} style={styles.scanBtn} />
            <Button label={text('beautyForm.fromLibrary')} iconName="gallery" variant="ghost" size="sm" fullWidth={false} onPress={() => runScan('library')} style={styles.scanBtn} />
          </View>
          {note ? (
            <AppText variant="caption" tone="muted" style={styles.note}>
              {note}
            </AppText>
          ) : null}
        </View>

        {error ? (
          <Card style={styles.errorCard}>
            <AppText variant="label" tone="danger">{`⚠ ${error}`}</AppText>
          </Card>
        ) : null}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacing.lg, marginTop: spacing.md },
  label: { marginBottom: spacing.sm },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  ingredients: { minHeight: 90, paddingTop: spacing.md, textAlignVertical: 'top' },
  scanRow: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.sm },
  scanBtn: { flex: 1 },
  note: { marginTop: spacing.sm },
  errorCard: {},
});
