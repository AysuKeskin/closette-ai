import { createNavigationContainerRef } from '@react-navigation/native';

import type { BeautyItem, Outfit, WardrobeItem } from '../api/types';
import type { AppStackParamList } from './types';

/** Root navigation ref so non-screen code (effects, banners) can open the
 * verify-email modal reliably, without depending on navigate bubbling through
 * deeply nested navigators. */
export const navigationRef = createNavigationContainerRef<AppStackParamList>();

export function openVerifyEmail() {
  if (navigationRef.isReady()) {
    navigationRef.navigate('VerifyEmail');
  }
}

export function openStylePreferences(onboarding?: boolean) {
  if (navigationRef.isReady()) {
    navigationRef.navigate('StylePreferences', onboarding ? { onboarding: true } : undefined);
  }
}

export function openOnboarding() {
  if (navigationRef.isReady()) {
    navigationRef.navigate('Onboarding');
  }
}

export function openStyleProfile() {
  if (navigationRef.isReady()) {
    navigationRef.navigate('StyleProfile');
  }
}

export function openSavedLooks() {
  if (navigationRef.isReady()) {
    navigationRef.navigate('SavedLooks');
  }
}

export function openLookDetail(look: Outfit) {
  if (navigationRef.isReady()) {
    navigationRef.navigate('LookDetail', { look });
  }
}

export function openFavorites() {
  if (navigationRef.isReady()) {
    navigationRef.navigate('Favorites');
  }
}

/**
 * Item and product detail live inside their tab's stack, so screens outside that
 * tab (Favorites, Home) have to address them through the nested route.
 *
 * `initial: false` is what keeps the tab usable. Without it, navigating into a
 * stack that has not been opened yet makes the detail screen that stack's ONLY
 * route: back cannot pop, so it escapes to the previous tab, and the stack stays
 * parked on the detail screen — leaving no way to reach the list again. With it,
 * the stack's own first screen sits underneath and back behaves normally.
 */
export function openItemDetail(item: WardrobeItem) {
  if (navigationRef.isReady()) {
    navigationRef.navigate('Tabs', {
      screen: 'WardrobeTab',
      params: { screen: 'ItemDetail', params: { item }, initial: false },
    });
  }
}

export function openBeautyDetail(item: BeautyItem) {
  if (navigationRef.isReady()) {
    navigationRef.navigate('Tabs', {
      screen: 'BeautyTab',
      params: { screen: 'BeautyDetail', params: { item }, initial: false },
    });
  }
}
