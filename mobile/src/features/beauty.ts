import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { beautyApi, type BeautyQuery } from '../api/endpoints';
import type { CreateBeautyPayload, UpdateBeautyPayload } from '../api/types';
import { queryKeys } from './queryClient';

export function useBeauty(query: BeautyQuery = {}) {
  return useQuery({
    queryKey: queryKeys.beauty(query),
    queryFn: () => beautyApi.list(query),
  });
}

/** Favourites only — what the Favorites screen shows. */
export function useFavoriteBeauty() {
  return useBeauty({ favorite: true });
}

export function useToggleBeautyFavorite() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => beautyApi.toggleFavorite(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['beauty'] }),
  });
}

export function useAnalyzeBeauty() {
  return useMutation({
    mutationFn: (args: { uri: string; mimeType: string; name: string }) =>
      beautyApi.analyze(args.uri, args.mimeType, args.name),
  });
}

export function useCreateBeauty() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateBeautyPayload) => beautyApi.create(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['beauty'] });
    },
  });
}

export function useUpdateBeauty() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; payload: UpdateBeautyPayload }) => beautyApi.update(args.id, args.payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['beauty'] }),
  });
}

export function useScanIngredients() {
  return useMutation({
    mutationFn: (args: { uri: string; mimeType: string; name: string }) =>
      beautyApi.scanIngredients(args.uri, args.mimeType, args.name),
  });
}

export function useDeleteBeauty() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => beautyApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['beauty'] }),
  });
}

export function useBeautySearch() {
  return useMutation({ mutationFn: (q: string) => beautyApi.search(q) });
}

export function useBeautyLookup() {
  return useMutation({ mutationFn: (barcode: string) => beautyApi.lookup(barcode) });
}

export function useExplainIngredient() {
  return useMutation({ mutationFn: (name: string) => beautyApi.explainIngredient(name) });
}
