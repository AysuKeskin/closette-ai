import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useMemo, useState } from 'react';
import { Image, StyleSheet, View } from 'react-native';

import { toApiError } from '../../api/client';
import { AppText, Button, Card, Chip, Header, Screen, TextField } from '../../components/ui';
import { BEAUTY_CATEGORIES, BeautyCategory } from '../../api/types';
import { useCreateBeauty } from '../../features/beauty';
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
  const navigation = useNavigation<NativeStackNavigationProp<BeautyStackParamList>>();
  const route = useRoute<RouteProp<BeautyStackParamList, 'ConfirmBeauty'>>();
  const { analysis: response, imageUri } = route.params;
  const ai = response.analysis;
  const createBeauty = useCreateBeauty();

  const [productName, setProductName] = useState(ai.productName ? titleCase(ai.productName) : '');
  const [brand, setBrand] = useState(ai.brand ?? '');
  const [category, setCategory] = useState<BeautyCategory>(toCategory(ai.category));
  const [size, setSize] = useState('');
  const [ingredients, setIngredients] = useState('');
  const [error, setError] = useState<string | null>(null);

  const lowConfidence = useMemo(() => ai.confidence > 0 && ai.confidence < 0.75, [ai.confidence]);

  const onSave = () => {
    setError(null);
    if (!productName.trim()) {
      setError('Please give this product a name.');
      return;
    }
    createBeauty.mutate(
      {
        productName: productName.trim(),
        brand: brand.trim() || undefined,
        category,
        size: size.trim() || undefined,
        ingredients: splitList(ingredients),
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
      footer={<Button label="Save to shelf" iconName="beauty" onPress={onSave} loading={createBeauty.isPending} />}
    >
      <Header title="Confirm details" subtitle="Edit anything before saving" onBack={() => navigation.goBack()} />

      {imageUri ? (
        <Image source={{ uri: imageUri }} style={styles.image} resizeMode="cover" />
      ) : null}

      {lowConfidence ? (
        <Card style={styles.confidence}>
          <AppText variant="label" tone="brand">
            We think this is a {ai.productName || 'product'}. Is that right? Feel free to fix anything.
          </AppText>
        </Card>
      ) : null}

      <View style={styles.form}>
        <TextField label="Product name" value={productName} onChangeText={setProductName} placeholder="e.g. Hydrating serum" />
        <TextField label="Brand (optional)" value={brand} onChangeText={setBrand} placeholder="e.g. The Ordinary" />

        <View>
          <AppText variant="label" tone="secondary" style={styles.fieldLabel}>
            Category
          </AppText>
          <View style={styles.chips}>
            {BEAUTY_CATEGORIES.map((c) => (
              <Chip key={c} label={titleCase(c)} selected={category === c} onPress={() => setCategory(c)} />
            ))}
          </View>
        </View>

        <TextField label="Size (optional)" value={size} onChangeText={setSize} placeholder="e.g. 30 ml" />
        <TextField
          label="Ingredients (optional)"
          value={ingredients}
          onChangeText={setIngredients}
          placeholder="niacinamide, hyaluronic acid"
          hint="Separate with commas"
          error={error}
        />
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
});
