import { NavigationContainer, DefaultTheme } from '@react-navigation/native';
import { useEffect } from 'react';

import { LoadingState, Screen } from '../components/ui';
import { colors } from '../theme';
import { useAuth } from '../store/auth';
import { AppStack } from './AppStack';
import { AuthStack } from './AuthStack';
import { navigationRef, openVerifyEmail } from './navigationRef';

const navTheme = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    background: colors.background,
    primary: colors.primary,
    card: colors.surface,
    text: colors.textPrimary,
    border: colors.border,
  },
};

export function RootNavigator() {
  const status = useAuth((s) => s.status);
  const hydrate = useAuth((s) => s.hydrate);
  const promptVerify = useAuth((s) => s.promptVerify);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  // Right after sign-up, open the verify-email modal once (soft — dismissable).
  useEffect(() => {
    if (status === 'authenticated' && promptVerify) {
      // Defer a tick so the AppStack has mounted and the ref is ready.
      const t = setTimeout(openVerifyEmail, 0);
      return () => clearTimeout(t);
    }
  }, [status, promptVerify]);

  return (
    <NavigationContainer ref={navigationRef} theme={navTheme}>
      {status === 'loading' ? (
        <Screen>
          <LoadingState message="Getting things ready…" />
        </Screen>
      ) : status === 'authenticated' ? (
        <AppStack />
      ) : (
        <AuthStack />
      )}
    </NavigationContainer>
  );
}
