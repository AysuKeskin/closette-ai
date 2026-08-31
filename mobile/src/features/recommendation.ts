import { useMutation } from '@tanstack/react-query';

import { recommendationApi } from '../api/endpoints';

/** Should-I-Buy from a natural-language description. */
export function useShouldIBuy() {
  return useMutation({
    mutationFn: (description: string) => recommendationApi.describe(description),
  });
}

/** Should-I-Buy from a photo of the item. */
export function useShouldIBuyPhoto() {
  return useMutation({
    mutationFn: (input: { uri: string; mimeType: string; name: string }) =>
      recommendationApi.fromPhoto(input.uri, input.mimeType, input.name),
  });
}
