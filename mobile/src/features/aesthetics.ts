import type { ImageSourcePropType } from 'react-native';

import type { TranslationKey } from '../i18n';

export type Aesthetic = {
  key: string;
  nameKey: TranslationKey;
  vibeKey: TranslationKey;
  palette: string[];
  tags: string[];
  image: ImageSourcePropType;
};

/** The eight style aesthetics shown in the onboarding swipe deck. `key` is the
 * stable identifier persisted in `lovedAesthetics`, so the profile recap can map
 * a saved key back to its title + illustration. */
export const AESTHETICS: Aesthetic[] = [
  { key: 'cleangirl', nameKey: 'aesthetics.cleangirl', vibeKey: 'aesthetics.cleangirlVibe', palette: ['#EFE7DA', '#D8C4A8', '#9A9A9A', '#1C1C1C'], tags: ['minimal', 'chic'], image: require('../assets/aesthetics/cleangirl.png') },
  { key: 'oldmoney', nameKey: 'aesthetics.oldmoney', vibeKey: 'aesthetics.oldmoneyVibe', palette: ['#C19A6B', '#22314E', '#EFE7DA', '#5E2233'], tags: ['classic', 'timeless', 'preppy'], image: require('../assets/aesthetics/oldmoney.png') },
  { key: 'coquette', nameKey: 'aesthetics.coquette', vibeKey: 'aesthetics.coquetteVibe', palette: ['#D9A6AF', '#EBC6CC', '#EFE7DA', '#B9A5D1'], tags: ['feminine', 'romantic'], image: require('../assets/aesthetics/coquette.png') },
  { key: 'street', nameKey: 'aesthetics.street', vibeKey: 'aesthetics.streetVibe', palette: ['#1C1C1C', '#9A9A9A', '#F7F7F5', '#B23A48'], tags: ['streetwear', 'edgy', 'sporty'], image: require('../assets/aesthetics/street.png') },
  { key: 'boho', nameKey: 'aesthetics.boho', vibeKey: 'aesthetics.bohoVibe', palette: ['#B08D57', '#6B6B3A', '#EFE7DA', '#C99A2E'], tags: ['boho', 'romantic'], image: require('../assets/aesthetics/boho.png') },
  { key: 'edgy', nameKey: 'aesthetics.edgy', vibeKey: 'aesthetics.edgyVibe', palette: ['#1C1C1C', '#5E2233', '#3A3A3A', '#C4C4C4'], tags: ['edgy'], image: require('../assets/aesthetics/edgy.png') },
  { key: 'minimalist', nameKey: 'aesthetics.minimalist', vibeKey: 'aesthetics.minimalistVibe', palette: ['#1C1C1C', '#F7F7F5', '#9A9A9A', '#D8C4A8'], tags: ['minimal'], image: require('../assets/aesthetics/minimalist.png') },
  { key: 'romantic', nameKey: 'aesthetics.romantic', vibeKey: 'aesthetics.romanticVibe', palette: ['#D9A6AF', '#B9A5D1', '#EFE7DA', '#C97B84'], tags: ['feminine', 'romantic', 'elegant'], image: require('../assets/aesthetics/romantic.png') },
];

export const AESTHETIC_BY_KEY: Record<string, Aesthetic> = Object.fromEntries(
  AESTHETICS.map((a) => [a.key, a]),
);
