import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { VerifyEmailScreen } from '../screens/auth/VerifyEmailScreen';
import { FavoritesScreen } from '../screens/favorites/FavoritesScreen';
import { LookDetailScreen } from '../screens/outfits/LookDetailScreen';
import { SavedLooksScreen } from '../screens/outfits/SavedLooksScreen';
import { OnboardingScreen } from '../screens/preferences/OnboardingScreen';
import { StylePreferencesScreen } from '../screens/preferences/StylePreferencesScreen';
import { StyleProfileScreen } from '../screens/preferences/StyleProfileScreen';
import { AppTabs } from './AppTabs';
import type { AppStackParamList } from './types';

const Stack = createNativeStackNavigator<AppStackParamList>();

/**
 * Authenticated area: the 5-tab app, plus modal screens presented over it
 * (verify email, style preferences / onboarding, saved looks, favourites).
 */
export function AppStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Tabs" component={AppTabs} />
      <Stack.Screen name="VerifyEmail" component={VerifyEmailScreen} options={{ presentation: 'modal' }} />
      <Stack.Screen name="Onboarding" component={OnboardingScreen} options={{ presentation: 'modal', gestureEnabled: true }} />
      <Stack.Screen name="StyleProfile" component={StyleProfileScreen} options={{ presentation: 'modal' }} />
      <Stack.Screen name="StylePreferences" component={StylePreferencesScreen} options={{ presentation: 'modal' }} />
      <Stack.Screen name="SavedLooks" component={SavedLooksScreen} />
      <Stack.Screen name="LookDetail" component={LookDetailScreen} />
      <Stack.Screen name="Favorites" component={FavoritesScreen} />
    </Stack.Navigator>
  );
}
