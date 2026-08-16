import { Image, Pressable, StyleSheet, View } from 'react-native';

import { colors, feedback, radius, spacing, typography } from '../../theme';
import { AppText } from './AppText';
import { Icon } from './Icon';

type Props = {
  title: string;
  subtitle?: string;
  imageUrl?: string | null;
  favorite?: boolean;
  width?: number;
  onPress?: () => void;
};

/** Image-forward tile for wardrobe/beauty items and Home rails. */
export function ItemTile({ title, subtitle, imageUrl, favorite, width, onPress }: Props) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole={onPress ? 'button' : undefined}
      accessibilityLabel={title}
      style={({ pressed }) => [styles.wrap, width ? { width } : undefined, pressed && styles.pressed]}
    >
      <View style={styles.imageWrap}>
        {imageUrl ? (
          <Image source={{ uri: imageUrl }} style={styles.image} resizeMode="cover" />
        ) : (
          <View style={[styles.image, styles.placeholder]}>
            <Icon name="garment" size={48} faded />
          </View>
        )}
        {favorite ? (
          <View style={styles.heart}>
            <Icon name="love" size={16} />
          </View>
        ) : null}
      </View>
      <AppText variant="label" numberOfLines={1} style={styles.title}>
        {title}
      </AppText>
      {subtitle ? (
        <AppText variant="caption" tone="muted" numberOfLines={1}>
          {subtitle}
        </AppText>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.xs },
  imageWrap: { position: 'relative' },
  image: {
    width: '100%',
    aspectRatio: 0.82,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceAlt,
  },
  placeholder: { alignItems: 'center', justifyContent: 'center' },
  heart: {
    position: 'absolute',
    top: spacing.sm,
    right: spacing.sm,
    width: 28,
    height: 28,
    borderRadius: radius.pill,
    backgroundColor: 'rgba(255,255,255,0.9)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  heartGlyph: { color: colors.primaryDark, fontSize: typography.size.label },
  title: { marginTop: spacing.xs },
  pressed: { opacity: feedback.pressOpacity, transform: [{ scale: feedback.pressScale }] },
});
