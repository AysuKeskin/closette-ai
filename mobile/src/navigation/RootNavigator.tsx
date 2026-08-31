import { NavigationContainer, DefaultTheme } from '@react-navigation/native';
import { useEffect } from 'react';

import { LoadingState, Screen } from '../components/ui';
import { useT } from '../i18n';
import { colors } from '../theme';
import { useAuth } from '../store/auth';
import { useLocale } from '../store/locale';
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
  const hydrateLocale = useLocale((s) => s.hydrate);
  const localeHydrated = useLocale((s) => s.hydrated);
  const { t } = useT();

  useEffect(() => {
    hydrate();
    // Read the saved language before anything renders, so a Turkish user never
    // sees an English frame first.
    hydrateLocale();
  }, [hydrate, hydrateLocale]);

  // Right after sign-up, open the verify-email modal once (soft — dismissable).
  useEffect(() => {
    if (status === 'authenticated' && promptVerify) {
      // Defer a tick so the AppStack has mounted and the ref is ready.
      const timer = setTimeout(openVerifyEmail, 0);
      return () => clearTimeout(timer);
    }
  }, [status, promptVerify]);

  return (
    <NavigationContainer ref={navigationRef} theme={navTheme}>
      {status === 'loading' || !localeHydrated ? (
        <Screen>
          <LoadingState message={t('common.loading')} />
        </Screen>
      ) : status === 'authenticated' ? (
        <AppStack />
      ) : (
        <AuthStack />
      )}
    </NavigationContainer>
  );
}
