import * as ImagePicker from 'expo-image-picker';
import { useState } from 'react';
import { Image, Pressable, StyleSheet, View } from 'react-native';

import { useT } from '../../i18n';
import { colors, feedback, palette, radius, spacing } from '../../theme';
import { AppText } from './AppText';
import { Icon } from './Icon';

type Props = {
  /** What the item shows today; null when it has no photo. */
  uri: string | null;
  /** Called with the picked asset, for the caller to upload. */
  onPick: (asset: { uri: string; mimeType: string; name: string }) => void;
  busy?: boolean;
};

/**
 * The item's photo while editing, and the two ways to replace it.
 *
 * Shared by the wardrobe and beauty edit screens so replacing a photo means the
 * same thing in both, down to which permission is asked for first.
 */
export function PhotoField({ uri, onPick, busy }: Props) {
  const { t } = useT();
  const [error, setError] = useState<string | null>(null);

  const pick = async (mode: 'camera' | 'library') => {
    setError(null);
    const permission =
      mode === 'camera'
        ? await ImagePicker.requestCameraPermissionsAsync()
        : await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      setError(t(mode === 'camera' ? 'item.permissionCamera' : 'item.permissionPhotos'));
      return;
    }
    const result =
      mode === 'camera'
        ? await ImagePicker.launchCameraAsync({ quality: 0.7 })
        : await ImagePicker.launchImageLibraryAsync({ quality: 0.7, mediaTypes: ['images'] });
    if (result.canceled || !result.assets?.[0]) return;

    const asset = result.assets[0];
    onPick({
      uri: asset.uri,
      mimeType: asset.mimeType ?? 'image/jpeg',
      name: asset.fileName ?? 'photo.jpg',
    });
  };

  return (
    <View style={styles.wrap}>
      <AppText variant="label" tone="secondary" style={styles.label}>
        {t('item.photo')}
      </AppText>
      <View style={styles.row}>
        {uri ? (
          <Image source={{ uri }} style={styles.preview} resizeMode="cover" />
        ) : (
          <View style={[styles.preview, styles.empty]}>
            <Icon name="camera" size={26} faded />
          </View>
        )}
        <View style={styles.actions}>
          <Action label={t('item.takePhoto')} icon="camera" onPress={() => void pick('camera')} disabled={busy} />
          <Action label={t('item.chooseFromLibrary')} icon="gallery" onPress={() => void pick('library')} disabled={busy} />
        </View>
      </View>
      {error ? (
        <AppText variant="caption" tone="danger" style={styles.error}>
          {error}
        </AppText>
      ) : null}
    </View>
  );
}

function Action({ label, icon, onPress, disabled }: {
  label: string; icon: 'camera' | 'gallery'; onPress: () => void; disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      disabled={disabled}
      android_ripple={{ color: feedback.ripple }}
      style={({ pressed }) => [styles.action, pressed && styles.actionPressed, disabled && styles.disabled]}
    >
      <Icon name={icon} size={18} />
      <AppText variant="label">{label}</AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.xs },
  label: { marginLeft: spacing.xs },
  row: { flexDirection: 'row', gap: spacing.md, alignItems: 'center' },
  preview: { width: 96, height: 120, borderRadius: radius.md, backgroundColor: palette.blush },
  empty: { alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.border },
  actions: { flex: 1, gap: spacing.sm },
  action: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  actionPressed: { transform: [{ scale: feedback.pressScaleCard }], opacity: feedback.pressOpacity },
  disabled: { opacity: 0.5 },
  error: { marginLeft: spacing.xs },
});
