import { QueryClient } from '@tanstack/react-query';

const makeQueryClient = () => new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
});

export let queryClient = makeQueryClient();

export function resetQueryClient() {
  const previous = queryClient;
  queryClient = makeQueryClient();
  void previous.cancelQueries();
  previous.clear();
}

export const queryKeys = {
  wardrobe: (params?: unknown) => ['wardrobe', params ?? {}] as const,
  wardrobeRecent: ['wardrobe', 'recent'] as const,
  wardrobeItem: (id: string) => ['wardrobe', 'item', id] as const,
  beauty: (query?: Record<string, unknown>) => ['beauty', query ?? {}] as const,
  beautyItem: (id: string) => ['beauty', 'item', id] as const,
  savedLooks: (limit?: number) => ['outfits', 'saved', limit ?? 'all'] as const,
};
