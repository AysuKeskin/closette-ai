import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { StyleSheet } from 'react-native';

import { Icon, IconName } from '../components/ui';
import { useT, type TranslationKey } from '../i18n';
import { colors, spacing, typography } from '../theme';
import { GetReadyScreen } from '../screens/getready/GetReadyScreen';
import { ProfileScreen } from '../screens/profile/ProfileScreen';
import { BeautyStack } from './BeautyStack';
import { HomeStack } from './HomeStack';
import { WardrobeStack } from './WardrobeStack';
import type { AppTabParamList } from './types';

const Tab = createBottomTabNavigator<AppTabParamList>();

// Custom dusty-pink icon set (see docs/ICONOGRAPHY.md).
const icons: Record<keyof AppTabParamList, IconName> = {
  HomeTab: 'home',
  WardrobeTab: 'wardrobe',
  GetReadyTab: 'getready',
  BeautyTab: 'beauty',
  ProfileTab: 'profile',
};

const labels: Record<keyof AppTabParamList, TranslationKey> = {
  HomeTab: 'tabs.home',
  WardrobeTab: 'tabs.wardrobe',
  GetReadyTab: 'tabs.getReady',
  BeautyTab: 'tabs.beauty',
  ProfileTab: 'tabs.profile',
};

export function AppTabs() {
  const { t } = useT();
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: colors.primaryDark,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarStyle: styles.bar,
        tabBarLabelStyle: styles.label,
        tabBarIcon: ({ focused }) => (
          <Icon name={icons[route.name as keyof AppTabParamList]} size={28} faded={!focused} />
        ),
        tabBarLabel: t(labels[route.name as keyof AppTabParamList]),
      })}
    >
      <Tab.Screen name="HomeTab" component={HomeStack} />
      <Tab.Screen name="WardrobeTab" component={WardrobeStack} />
      <Tab.Screen name="GetReadyTab" component={GetReadyScreen} />
      <Tab.Screen name="BeautyTab" component={BeautyStack} />
      <Tab.Screen name="ProfileTab" component={ProfileScreen} />
    </Tab.Navigator>
  );
}

const styles = StyleSheet.create({
  bar: {
    backgroundColor: colors.surface,
    borderTopColor: colors.border,
    height: 88,
    paddingTop: spacing.sm,
    paddingBottom: spacing.xl,
  },
  label: { fontSize: typography.size.caption, fontWeight: typography.weight.medium },
});
