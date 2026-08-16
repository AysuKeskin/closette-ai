import { createNavigationContainerRef } from '@react-navigation/native';

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
