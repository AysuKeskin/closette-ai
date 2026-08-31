import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { BeautyScreen } from '../screens/beauty/BeautyScreen';
import { AddBeautyScreen } from '../screens/beauty/AddBeautyScreen';
import { BeautyDetailScreen } from '../screens/beauty/BeautyDetailScreen';
import { BeautySearchScreen } from '../screens/beauty/BeautySearchScreen';
import { ConfirmBeautyScreen } from '../screens/beauty/ConfirmBeautyScreen';
import { EditBeautyScreen } from '../screens/beauty/EditBeautyScreen';
import type { BeautyStackParamList } from './types';

const Stack = createNativeStackNavigator<BeautyStackParamList>();

export function BeautyStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="BeautyList" component={BeautyScreen} />
      <Stack.Screen name="AddBeauty" component={AddBeautyScreen} />
      <Stack.Screen name="BeautySearch" component={BeautySearchScreen} />
      <Stack.Screen name="ConfirmBeauty" component={ConfirmBeautyScreen} />
      <Stack.Screen name="BeautyDetail" component={BeautyDetailScreen} />
      <Stack.Screen name="EditBeauty" component={EditBeautyScreen} />
    </Stack.Navigator>
  );
}
