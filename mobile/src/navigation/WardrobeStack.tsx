import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { AddItemScreen } from '../screens/wardrobe/AddItemScreen';
import { ConfirmItemScreen } from '../screens/wardrobe/ConfirmItemScreen';
import { WardrobeListScreen } from '../screens/wardrobe/WardrobeListScreen';
import type { WardrobeStackParamList } from './types';

const Stack = createNativeStackNavigator<WardrobeStackParamList>();

export function WardrobeStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="WardrobeList" component={WardrobeListScreen} />
      <Stack.Screen name="AddItem" component={AddItemScreen} />
      <Stack.Screen name="ConfirmItem" component={ConfirmItemScreen} />
    </Stack.Navigator>
  );
}
