import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
});

export const queryKeys = {
  wardrobe: (params?: unknown) => ['wardrobe', params ?? {}] as const,
  wardrobeRecent: ['wardrobe', 'recent'] as const,
  beauty: (query?: Record<string, unknown>) => ['beauty', query ?? {}] as const,
  savedLooks: ['outfits', 'saved'] as const,
};
