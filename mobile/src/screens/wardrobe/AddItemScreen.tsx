import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import * as ImagePicker from 'expo-image-picker';
import { useState } from 'react';
import { Image, StyleSheet, View } from 'react-native';

import { toApiError } from '../../api/client';
import { AppText, Button, Card, Header, Icon, LoadingState, Screen } from '../../components/ui';
import { useAnalyzeItem } from '../../features/wardrobe';
import { colors, radius, spacing } from '../../theme';
import type { WardrobeStackParamList } from '../../navigation/types';

export function AddItemScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<WardrobeStackParamList>>();
  const analyze = useAnalyzeItem();
  const [imageUri, setImageUri] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const pick = async (mode: 'camera' | 'library') => {
    setError(null);
    const perm =
      mode === 'camera'
        ? await ImagePicker.requestCameraPermissionsAsync()
        : await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) {
      setError('We need permission to access your ' + (mode === 'camera' ? 'camera' : 'photos') + '.');
      return;
    }
    const result =
      mode === 'camera'
        ? await ImagePicker.launchCameraAsync({ quality: 0.7 })
        : await ImagePicker.launchImageLibraryAsync({ quality: 0.7, mediaTypes: ['images'] });
    if (result.canceled || !result.assets?.[0]) return;

    const asset = result.assets[0];
    setImageUri(asset.uri);
    runAnalysis(asset.uri, asset.mimeType ?? 'image/jpeg', asset.fileName ?? 'item.jpg');
  };

  const runAnalysis = (uri: string, mimeType: string, name: string) => {
    analyze.mutate(
      { uri, mimeType, name },
      {
        onSuccess: (data) => navigation.replace('ConfirmItem', { analysis: data, imageUri: uri }),
        onError: (err) => setError(toApiError(err).message),
      },
    );
  };

  return (
    <Screen scroll>
      <Header title="Add an item" subtitle="Snap it — we'll fill in the details" onBack={() => navigation.goBack()} />

      <Card style={styles.preview} padded={false}>
        {imageUri ? (
          <Image source={{ uri: imageUri }} style={styles.image} resizeMode="cover" />
        ) : (
          <View style={styles.placeholder}>
            <Icon name="camera" size={72} />
            <AppText variant="body" tone="secondary" center>
              Take a photo or choose one from your library
            </AppText>
          </View>
        )}
      </Card>

      {analyze.isPending ? (
        <LoadingState message="Analyzing your item…" />
      ) : (
        <View style={styles.actions}>
          <Button label="Take photo" iconName="camera" onPress={() => pick('camera')} />
          <Button label="Choose from library" iconName="gallery" variant="secondary" onPress={() => pick('library')} />
        </View>
      )}

      {error ? (
        <Card style={styles.errorCard}>
          <AppText variant="label" tone="danger">
            {`⚠ ${error}`}
          </AppText>
          <Button
            label="Add manually instead"
            variant="ghost"
            style={styles.manual}
            onPress={() =>
              navigation.replace('ConfirmItem', {
                analysis: {
                  imageKey: '',
                  imageUrl: null,
                  analysis: {
                    category: 'top',
                    subcategory: '',
                    colors: [],
                    pattern: '',
                    styles: [],
                    seasons: [],
                    confidence: 0,
                  },
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
