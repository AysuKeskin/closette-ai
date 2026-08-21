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
  /** When set, a visible ✕ delete button appears in the corner. */
  onDelete?: () => void;
};

/** Image-forward tile for wardrobe/beauty items and Home rails. */
export function ItemTile({ title, subtitle, imageUrl, favorite, width, onPress, onDelete }: Props) {
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
            <Icon name="garment" size={44} faded />
          </View>
        )}
        {favorite ? (
          <View style={[styles.badge, onDelete ? styles.badgeLeft : styles.badgeRight]}>
            <Icon name="love" size={15} />
          </View>
        ) : null}
        {onDelete ? (
          <Pressable
            onPress={onDelete}
            hitSlop={10}
            accessibilityRole="button"
            accessibilityLabel={`Delete ${title}`}
            style={({ pressed }) => [styles.delete, pressed && styles.deletePressed]}
          >
            <AppText style={styles.deleteGlyph}>✕</AppText>
          </Pressable>
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

const BADGE = 26;

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
  badge: {
    position: 'absolute',
    top: spacing.xs,
    width: BADGE,
    height: BADGE,
    borderRadius: radius.pill,
    backgroundColor: 'rgba(255,255,255,0.92)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeRight: { right: spacing.xs },
  badgeLeft: { left: spacing.xs },
  delete: {
    position: 'absolute',
    top: spacing.xs,
    right: spacing.xs,
    width: BADGE,
    height: BADGE,
    borderRadius: radius.pill,
    backgroundColor: 'rgba(255,255,255,0.95)',
    borderWidth: 1,
    borderColor: colors.danger,
    alignItems: 'center',
    justifyContent: 'center',
  },
  deletePressed: { backgroundColor: colors.danger },
  deleteGlyph: { color: colors.danger, fontSize: typography.size.label, fontWeight: typography.weight.bold, lineHeight: 16 },
  title: { marginTop: spacing.xs },
  pressed: { opacity: feedback.pressOpacity, transform: [{ scale: feedback.pressScale }] },
});
