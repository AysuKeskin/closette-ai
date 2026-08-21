import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { beautyApi } from '../api/endpoints';
import type { CreateBeautyPayload } from '../api/types';
import { queryKeys } from './queryClient';

export function useBeauty(category?: string) {
  return useQuery({
    queryKey: queryKeys.beauty(category),
    queryFn: () => beautyApi.list(category),
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

export function useDeleteBeauty() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => beautyApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['beauty'] }),
  });
}
