import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import * as ImagePicker from 'expo-image-picker';
import { useState } from 'react';
import { Image, StyleSheet, View } from 'react-native';

import { toApiError } from '../../api/client';
import { AppText, Button, Card, Header, Icon, LoadingState, Screen } from '../../components/ui';
import { useAnalyzeBeauty } from '../../features/beauty';
import { useT } from '../../i18n';
import { colors, radius, spacing } from '../../theme';
import type { BeautyStackParamList } from '../../navigation/types';

export function AddBeautyScreen() {
  const { t: text } = useT();
  const navigation = useNavigation<NativeStackNavigationProp<BeautyStackParamList>>();
  const analyze = useAnalyzeBeauty();
  const [imageUri, setImageUri] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const pick = async (mode: 'camera' | 'library') => {
    setError(null);
    const perm =
      mode === 'camera'
        ? await ImagePicker.requestCameraPermissionsAsync()
        : await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) {
      setError(text(mode === 'camera' ? 'item.permissionCamera' : 'item.permissionPhotos'));
      return;
    }
    const result =
      mode === 'camera'
        ? await ImagePicker.launchCameraAsync({ quality: 0.7 })
        : await ImagePicker.launchImageLibraryAsync({ quality: 0.7, mediaTypes: ['images'] });
    if (result.canceled || !result.assets?.[0]) return;

    const asset = result.assets[0];
    setImageUri(asset.uri);
    runAnalysis(asset.uri, asset.mimeType ?? 'image/jpeg', asset.fileName ?? 'product.jpg');
  };

  const runAnalysis = (uri: string, mimeType: string, name: string) => {
    analyze.mutate(
      { uri, mimeType, name },
      {
        onSuccess: (data) => navigation.replace('ConfirmBeauty', { analysis: data, imageUri: uri }),
        onError: (err) => setError(toApiError(err).message),
      },
    );
  };

  return (
    <Screen scroll>
      <Header title={text('beautyForm.addTitle')} subtitle={text('beautyForm.addSubtitle')} onBack={() => navigation.goBack()} />

      <Card style={styles.preview} padded={false}>
        {imageUri ? (
          <Image source={{ uri: imageUri }} style={styles.image} resizeMode="cover" />
        ) : (
          <View style={styles.placeholder}>
            <Icon name="beauty" size={72} />
            <AppText variant="body" tone="secondary" center>
              {text('beautyForm.addHint')}
            </AppText>
          </View>
        )}
      </Card>

      {analyze.isPending ? (
        <LoadingState message={text('beautyForm.analyzing')} />
      ) : (
        <View style={styles.actions}>
          <Button label={text('beautyForm.searchByName')} iconName="shop" onPress={() => navigation.navigate('BeautySearch')} />
          <Button label={text('beautyForm.takePhoto')} iconName="camera" variant="secondary" onPress={() => pick('camera')} />
          <Button label={text('beautyForm.chooseFromLibrary')} iconName="gallery" variant="ghost" onPress={() => pick('library')} />
        </View>
      )}

      {error ? (
        <Card style={styles.errorCard}>
          <AppText variant="label" tone="danger">
            {`⚠ ${error}`}
          </AppText>
          <Button
            label={text('beautyForm.addManually')}
            variant="ghost"
            style={styles.manual}
            onPress={() =>
              navigation.replace('ConfirmBeauty', {
                analysis: {
                  imageKey: '',
                  imageUrl: null,
                  analysis: { brand: '', productName: '', category: 'skincare', confidence: 0 },
                },
                imageUri: imageUri ?? '',
              })
            }
          />
        </Card>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  preview: {
    marginTop: spacing.md,
    height: 320,
    borderRadius: radius.lg,
    overflow: 'hidden',
    backgroundColor: colors.surfaceAlt,
  },
  image: { width: '100%', height: '100%' },
  placeholder: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.md, padding: spacing.xl },
  actions: { gap: spacing.md, marginTop: spacing.xl },
  errorCard: { marginTop: spacing.xl, gap: spacing.md },
  manual: { marginTop: spacing.xs },
});
