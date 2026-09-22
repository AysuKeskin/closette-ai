import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { outfitApi } from '../api/endpoints';
import { queryKeys } from './queryClient';

export function useGenerateLook() {
  return useMutation({
    mutationFn: (args: { prompt: string; occasion?: string; excludeItemIds?: string[] }) =>
      outfitApi.generate(args.prompt, args.occasion, args.excludeItemIds),
  });
}

export function useSaveLook() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (p: { title: string; occasion?: string; rationale?: string; itemIds: string[] }) =>
      outfitApi.save(p),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['outfits'] }),
  });
}

export function useDeleteLook() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => outfitApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['outfits'] }),
  });
}

/**
 * Saved looks, newest first.
 *
 * `limit` is for a screen that only shows a few: every look carries its pieces
 * in full, so asking for all of them to fill one strip on Home downloads a year
 * of outfits to draw three.
 */
export function useSavedLooks(limit?: number) {
  return useQuery({
    queryKey: queryKeys.savedLooks(limit),
    queryFn: () => outfitApi.saved(limit),
  });
}
