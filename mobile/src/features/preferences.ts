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

/**
 * Whether the quiz actually produced something to style with.
 *
 * `onboardingCompleted` alone does not answer this: "Skip for now" used to set
 * the flag while collecting nothing, so accounts exist that claim a style
 * profile and carry an empty one. Reading the data heals those without a
 * migration, and keeps the claim honest going forward.
 */
export function hasStyleProfile(prefs?: StylePreference | null): boolean {
  if (!prefs) return false;
  return Boolean(
    prefs.colorSeason || prefs.dressUp || prefs.preferredStyles.length || prefs.lovedAesthetics.length,
  );
}
