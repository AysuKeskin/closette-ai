import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { BeautyScreen } from '../screens/beauty/BeautyScreen';
import { AddBeautyScreen } from '../screens/beauty/AddBeautyScreen';
import { ConfirmBeautyScreen } from '../screens/beauty/ConfirmBeautyScreen';
import type { BeautyStackParamList } from './types';

const Stack = createNativeStackNavigator<BeautyStackParamList>();

export function BeautyStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="BeautyList" component={BeautyScreen} />
      <Stack.Screen name="AddBeauty" component={AddBeautyScreen} />
      <Stack.Screen name="ConfirmBeauty" component={ConfirmBeautyScreen} />
    </Stack.Navigator>
  );
}
