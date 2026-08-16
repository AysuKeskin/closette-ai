import { useMutation, useQuery } from '@tanstack/react-query';

import { outfitApi } from '../api/endpoints';
import { queryKeys } from './queryClient';

export function useGenerateLook() {
  return useMutation({
    mutationFn: (args: { prompt: string; occasion?: string }) =>
      outfitApi.generate(args.prompt, args.occasion),
  });
}

export function useSavedLooks() {
  return useQuery({
    queryKey: queryKeys.savedLooks,
    queryFn: () => outfitApi.saved(),
  });
}
