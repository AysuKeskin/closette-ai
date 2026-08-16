import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { VerifyEmailScreen } from '../screens/auth/VerifyEmailScreen';
import { AppTabs } from './AppTabs';
import type { AppStackParamList } from './types';

const Stack = createNativeStackNavigator<AppStackParamList>();

/**
 * Authenticated area: the 5-tab app, plus the verify-email screen presented as a
 * modal over it (soft verification — always dismissable).
 */
export function AppStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Tabs" component={AppTabs} />
      <Stack.Screen name="VerifyEmail" component={VerifyEmailScreen} options={{ presentation: 'modal' }} />
    </Stack.Navigator>
  );
}
