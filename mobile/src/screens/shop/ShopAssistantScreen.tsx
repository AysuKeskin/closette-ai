import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import * as ImagePicker from 'expo-image-picker';
import { useState } from 'react';
import { FlatList, Image, Pressable, StyleSheet, View } from 'react-native';

import { toApiError } from '../../api/client';
import {
  AppText,
  Button,
  Card,
  Header,
  Icon,
  ItemTile,
  LoadingState,
  Screen,
  TextField,
  VerifyBanner,
} from '../../components/ui';
import { BuyVerdict, ShouldIBuyResult } from '../../api/types';
import { useShouldIBuy, useShouldIBuyPhoto } from '../../features/recommendation';
import { openVerifyEmail } from '../../navigation/navigationRef';
import { useT, type TranslationKey } from '../../i18n';
import { useDomainLabels } from '../../i18n/domain';
import { colors, feedback, radius, spacing } from '../../theme';
import type { HomeStackParamList } from '../../navigation/types';

// `tone` is a theme colour; the tints are derived from it with alpha, nothing off-palette.
const VERDICTS: Record<BuyVerdict, { label: TranslationKey; sub: TranslationKey; tone: string }> = {
  buy: { label: 'shop.verdict.buyLabel', sub: 'shop.verdict.buySub', tone: colors.success },
  maybe: { label: 'shop.verdict.maybeLabel', sub: 'shop.verdict.maybeSub', tone: colors.warning },
  skip: { label: 'shop.verdict.skipLabel', sub: 'shop.verdict.skipSub', tone: colors.danger },
};

export function ShopAssistantScreen() {
  const { t: copy } = useT();
  const labels = useDomainLabels();
  const navigation = useNavigation<NativeStackNavigationProp<HomeStackParamList>>();
  const describe = useShouldIBuy();
  const fromPhoto = useShouldIBuyPhoto();
  const [text, setText] = useState('');
  const [imageUri, setImageUri] = useState<string | null>(null);
  const [result, setResult] = useState<ShouldIBuyResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [needsVerify, setNeedsVerify] = useState(false);

  const pending = describe.isPending || fromPhoto.isPending;

  const onResult = (data: ShouldIBuyResult) => setResult(data);
  const onError = (err: unknown) => {
    const api = toApiError(err);
    if (api.code === 'EMAIL_NOT_VERIFIED') setNeedsVerify(true);
    else setError(api.message);
  };
  const reset = () => {
    setError(null);
    setNeedsVerify(false);
    setResult(null);
  };

  const runText = () => {
    const value = text.trim();
    if (!value) return;
    reset();
    setImageUri(null);
    describe.mutate(value, { onSuccess: onResult, onError });
  };

  const runPhoto = async (mode: 'camera' | 'library') => {
    reset();
    const perm =
      mode === 'camera'
        ? await ImagePicker.requestCameraPermissionsAsync()
        : await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) {
      setError(`We need permission to access your ${mode === 'camera' ? 'camera' : 'photos'}.`);
      return;
    }
    const picked =
      mode === 'camera'
        ? await ImagePicker.launchCameraAsync({ quality: 0.7 })
        : await ImagePicker.launchImageLibraryAsync({ quality: 0.7, mediaTypes: ['images'] });
    if (picked.canceled || !picked.assets?.[0]) return;
    const asset = picked.assets[0];
    setText('');
    setImageUri(asset.uri);
    fromPhoto.mutate(
      { uri: asset.uri, mimeType: asset.mimeType ?? 'image/jpeg', name: asset.fileName ?? 'item.jpg' },
      { onSuccess: onResult, onError },
    );
  };

  const verdict = result ? VERDICTS[result.verdict] : null;

  return (
    <Screen scroll>
      <Header
        title={copy('shop.title')}
        subtitle={copy('shop.subtitle')}
        onBack={() => navigation.goBack()}
        stackedBack
      />

      <TextField
        placeholder={copy('shop.placeholder')}
        value={text}
        onChangeText={setText}
        multiline
        style={styles.input}
        onSubmitEditing={runText}
      />

      <Button
        label={copy('shop.check')}
        iconName="shop"
        onPress={runText}
        loading={describe.isPending}
        style={styles.cta}
      />

      <View style={styles.orRow}>
        <View style={styles.rule} />
        <AppText variant="caption" tone="muted">
          {copy('shop.orUsePhoto')}
        </AppText>
        <View style={styles.rule} />
      </View>

      <View style={styles.photoRow}>
        <Button
          label={copy('shop.takePhoto')}
          iconName="camera"
          variant="secondary"
          onPress={() => runPhoto('camera')}
          fullWidth={false}
          style={styles.photoBtn}
        />
        <Button
          label={copy('shop.fromLibrary')}
          iconName="gallery"
          variant="ghost"
          onPress={() => runPhoto('library')}
          fullWidth={false}
          style={styles.photoBtn}
        />
      </View>

      {imageUri && pending ? (
        <Image source={{ uri: imageUri }} style={styles.preview} resizeMode="cover" />
      ) : null}

      {pending ? <LoadingState message={copy('shop.reading')} /> : null}

      {needsVerify ? (
        <View style={styles.block}>
          <VerifyBanner onPress={openVerifyEmail} message={copy('shop.verifyNeeded')} />
        </View>
      ) : null}

      {error ? (
        <Card style={styles.block}>
          <AppText variant="label" tone="danger">{`⚠ ${error}`}</AppText>
        </Card>
      ) : null}

      {result && !result.understood && !pending ? (
        <Card style={styles.block}>
          <AppText variant="title">{copy('shop.unreadableTitle')}</AppText>
          <AppText variant="body" tone="secondary" style={styles.explanation}>
            {result.explanation}
          </AppText>
        </Card>
      ) : null}

      {result && result.understood && verdict && !pending ? (
        <View style={styles.block}>
          {result.detectedLabel ? (
            <View style={styles.understood}>
              <Icon name="ai-magic" size={18} />
              <AppText variant="label" tone="secondary" style={styles.understoodText}>
                {copy('shop.gotIt', { label: result.detectedLabel })}
              </AppText>
            </View>
          ) : null}

          {/* Verdict hero — the answer leads, with the fit score beside it. */}
          <View style={[styles.hero, { backgroundColor: verdict.tone + '14', borderColor: verdict.tone + '3D' }]}>
            <View style={styles.heroText}>
              <AppText variant="h1" style={{ color: verdict.tone }}>
                {copy(verdict.label)}
              </AppText>
              <AppText variant="caption" tone="muted">
                {copy(verdict.sub)}
              </AppText>
            </View>
            <View style={styles.fitBox}>
              <AppText variant="h2" style={{ color: verdict.tone }}>
                {result.matchScore}%
              </AppText>
              <AppText variant="caption" tone="muted">
                {copy('shop.wardrobeMatch')}
              </AppText>
            </View>
          </View>

          <AppText variant="body" style={styles.explanation}>
            {result.explanation}
          </AppText>

          {result.similarItems.length > 0 ? (
            <View style={styles.similar}>
              <AppText variant="label" tone="secondary" style={styles.similarTitle}>
                {copy('shop.similarTitle')}
              </AppText>
              <FlatList
                horizontal
                data={result.similarItems}
                keyExtractor={(i) => i.id}
                showsHorizontalScrollIndicator={false}
                ItemSeparatorComponent={() => <View style={{ width: spacing.md }} />}
                renderItem={({ item }) => (
                  <ItemTile
                    width={110}
                    title={item.name}
                    subtitle={labels.clothingCategory(item.category)}
                    imageUrl={item.imageUrl}
                  />
                )}
              />
            </View>
          ) : null}
        </View>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  input: { minHeight: 76, paddingTop: spacing.md, textAlignVertical: 'top', marginTop: spacing.md },
  cta: { marginTop: spacing.lg },

  orRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, marginVertical: spacing.xl },
  rule: { flex: 1, height: StyleSheet.hairlineWidth, backgroundColor: colors.border },
  photoRow: { flexDirection: 'row', gap: spacing.md },
  photoBtn: { flex: 1 },
  preview: { width: '100%', height: 200, borderRadius: radius.lg, marginTop: spacing.lg },

  block: { marginTop: spacing.xl },

  understood: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, marginBottom: spacing.md },
  understoodText: { flex: 1 },

  hero: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: radius.lg,
    borderWidth: 1,
    padding: spacing.lg,
    gap: spacing.md,
  },
  heroText: { flex: 1, gap: 2 },
  fitBox: { alignItems: 'center' },

  explanation: { lineHeight: 22, marginTop: spacing.lg },

  similar: { marginTop: spacing.xl },
  similarTitle: { marginBottom: spacing.sm },
});
