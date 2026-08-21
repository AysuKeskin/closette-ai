import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { CreateItemPayload, WardrobeQuery, wardrobeApi } from '../api/endpoints';
import { queryKeys } from './queryClient';

export function useWardrobe(query: WardrobeQuery = {}) {
  return useQuery({
    queryKey: queryKeys.wardrobe(query),
    queryFn: () => wardrobeApi.list(query),
  });
}

export function useRecentItems(limit = 8) {
  return useQuery({
    queryKey: queryKeys.wardrobeRecent,
    queryFn: () => wardrobeApi.recent(limit),
  });
}

export function useSimilarItems(id: string, limit = 6) {
  return useQuery({
    queryKey: ['wardrobe', 'similar', id],
    queryFn: () => wardrobeApi.similar(id, limit),
  });
}

export function useAnalyzeItem() {
  return useMutation({
    mutationFn: (args: { uri: string; mimeType: string; name: string }) =>
      wardrobeApi.analyze(args.uri, args.mimeType, args.name),
  });
}

export function useCreateItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateItemPayload) => wardrobeApi.create(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['wardrobe'] });
    },
  });
}

export function useToggleFavorite() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => wardrobeApi.toggleFavorite(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['wardrobe'] }),
  });
}

export function useDeleteItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => wardrobeApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['wardrobe'] }),
  });
}
