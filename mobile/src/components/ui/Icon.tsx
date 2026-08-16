import { Image, ImageStyle, StyleProp } from 'react-native';

/**
 * The custom Closette icon set (full-color raster art, dusty-pink line style).
 * Screens reference icons by semantic name — `<Icon name="ai-magic" />` — the
 * same way colors come from tokens. See docs/ICONOGRAPHY.md for what each means.
 */
const SOURCES = {
  home: require('../../assets/icons/home.png'),
  wardrobe: require('../../assets/icons/wardrobe.png'),
  getready: require('../../assets/icons/getready.png'),
  beauty: require('../../assets/icons/beauty.png'),
  profile: require('../../assets/icons/profile.png'),
  shop: require('../../assets/icons/shop.png'),
  'ai-magic': require('../../assets/icons/ai-magic.png'),
  camera: require('../../assets/icons/camera.png'),
  gallery: require('../../assets/icons/gallery.png'),
  garment: require('../../assets/icons/garment.png'),
  brand: require('../../assets/icons/brand.png'),
  palette: require('../../assets/icons/palette.png'),
  save: require('../../assets/icons/save.png'),
  love: require('../../assets/icons/love.png'),
  favorite: require('../../assets/icons/favorite.png'),
  warning: require('../../assets/icons/warning.png'),
  envelope: require('../../assets/icons/envelope.png'),
  privacy: require('../../assets/icons/privacy.png'),
  info: require('../../assets/icons/info.png'),
  dislike: require('../../assets/icons/dislike.png'),
} as const;

export type IconName = keyof typeof SOURCES;

type Props = {
  name: IconName;
  size?: number;
  style?: StyleProp<ImageStyle>;
  /** Dim for inactive/disabled states (icons are full-color, so we fade not tint). */
  faded?: boolean;
};

export function Icon({ name, size = 24, style, faded }: Props) {
  return (
    <Image
      source={SOURCES[name]}
      resizeMode="contain"
      style={[{ width: size, height: size }, faded && { opacity: 0.4 }, style]}
    />
  );
}
