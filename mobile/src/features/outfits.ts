import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { outfitApi } from '../api/endpoints';
import { queryKeys } from './queryClient';

export function useGenerateLook() {
  return useMutation({
    mutationFn: (args: { prompt: string; occasion?: string }) =>
      outfitApi.generate(args.prompt, args.occasion),
  });
}

export function useSaveLook() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (p: { title: string; occasion?: string; itemIds: string[] }) => outfitApi.save(p),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['outfits'] }),
  });
}

export function useSavedLooks() {
  return useQuery({
    queryKey: queryKeys.savedLooks,
    queryFn: () => outfitApi.saved(),
  });
}
