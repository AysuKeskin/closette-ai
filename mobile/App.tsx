import { QueryClientProvider } from '@tanstack/react-query';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { queryClient } from './src/features/queryClient';
import { useAuth } from './src/store/auth';
import { RootNavigator } from './src/navigation/RootNavigator';

export default function App() {
  const epoch = useAuth((state) => state.sessionEpoch);
  return (
    <SafeAreaProvider>
      <QueryClientProvider key={epoch} client={queryClient}>
        <StatusBar style="dark" />
        <RootNavigator />
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}
