import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { userApi } from '../api/endpoints';
import type { StylePreference } from '../api/types';

export function useStylePreferences() {
  return useQuery({
    queryKey: ['style-preferences'],
    queryFn: () => userApi.getStylePreferences(),
  });
}

export function useUpdateStylePreferences() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: Partial<StylePreference>) => userApi.updateStylePreferences(payload),
    onSuccess: (data) => qc.setQueryData(['style-preferences'], data),
  });
}
