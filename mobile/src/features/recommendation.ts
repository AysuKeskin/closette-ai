import { useMutation } from '@tanstack/react-query';

import { recommendationApi } from '../api/endpoints';
import type { ClothingCategory } from '../api/types';

export function useShouldIBuy() {
  return useMutation({
    mutationFn: (input: { category?: ClothingCategory; colors?: string[]; styles?: string[] }) =>
      recommendationApi.shouldIBuy(input),
  });
}
