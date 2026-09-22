import { useQuery } from '@tanstack/react-query';

import { usageApi } from '../api/endpoints';
import type { Allowance } from '../api/types';
import { queryKeys } from './queryClient';

/**
 * What the account may still spend on AI this month.
 *
 * Refetched whenever the app comes back to the foreground, because the counts
 * move on the server: another device, or simply the month turning over.
 */
export function useAllowances() {
  return useQuery({
    queryKey: queryKeys.allowances,
    queryFn: () => usageApi.allowances(),
    staleTime: 30_000,
  });
}

/** One operation's standing, or undefined until the counts have loaded. */
export function allowanceFor(allowances: Allowance[] | undefined, operation: string) {
  return allowances?.find((a) => a.operation === operation);
}
