import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { WardrobeItem } from '../api/types';
import { CreateItemPayload, UpdateItemPayload, WardrobeQuery, wardrobeApi } from '../api/endpoints';
import { queryKeys } from './queryClient';

export function useWardrobe(query: WardrobeQuery = {}) {
  return useQuery({
    queryKey: queryKeys.wardrobe(query),
    queryFn: () => wardrobeApi.list(query),
  });
}

/** Favourites only — what the Favorites screen shows. */
export function useFavoriteItems() {
  return useWardrobe({ favorite: true });
}

export function useRecentItems(limit = 8) {
  return useQuery({
    queryKey: queryKeys.wardrobeRecent,
    queryFn: () => wardrobeApi.recent(limit),
  });
}

/**
 * One item, kept fresh.
 *
 * The detail screen is reached with the item in its route params, which is a
 * snapshot: after an edit it would still render the values from before. Seeding
 * the query with that snapshot keeps the screen instant, and an edit invalidates
 * it so the saved values appear.
 */
export function useItem(id: string, initial: WardrobeItem) {
  return useQuery({
    queryKey: queryKeys.wardrobeItem(id),
    queryFn: () => wardrobeApi.get(id),
    initialData: initial,
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

export function useUpdateItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; payload: UpdateItemPayload }) =>
      wardrobeApi.update(args.id, args.payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['wardrobe'] }),
  });
}

/** Re-derives the catalogue tags of pieces already saved. */
export function useRetagWardrobe() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => wardrobeApi.retag(),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['wardrobe'] }),
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
