import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import { Alert, Linking } from 'react-native';
import { translate } from '../i18n';
import { useAuth } from '../store/auth';
import { currentLanguage } from '../store/locale';

type Disclosure = {
  version: string;
  provider: string;
  processingDetails: string;
  requiresConsent: boolean;
  privacyUrl: string;
};

let accepted: { epoch: number; version: string } | null = null;
let pending: { epoch: number; promise: Promise<void> } | null = null;

export function needsAiConsent(config: Pick<InternalAxiosRequestConfig, 'url' | 'method'>): boolean {
  return (config.method?.toLowerCase() === 'post' && [
    '/api/wardrobe/analyze', '/api/wardrobe/items/retag', '/api/beauty/analyze',
    '/api/beauty/ingredients/scan', '/api/outfits/generate',
    '/api/recommendations/should-i-buy', '/api/recommendations/should-i-buy/photo',
  ].includes(config.url ?? '')) || config.url === '/api/beauty/ingredients/explain';
}

export function forgetAiConsent() { accepted = null; }

export async function openLegalPage(url: string): Promise<void> {
  try { await Linking.openURL(url); }
  catch { Alert.alert(translate(currentLanguage(), 'consent.linkFailed')); }
}

export function ensureAiConsent(client: AxiosInstance, epoch: number, approve?: (version: string) => Promise<void>): Promise<void> {
  if (pending?.epoch === epoch) return pending.promise;
  const current = { epoch, promise: requestConsent(client, epoch, approve) };
  pending = current;
  void current.promise.then(
    () => { if (pending === current) pending = null; },
    () => { if (pending === current) pending = null; },
  );
  return current.promise;
}

async function requestConsent(client: AxiosInstance, epoch: number, approve?: (version: string) => Promise<void>) {
  const { data } = await client.get<Disclosure>('/api/privacy');
  if (epoch !== useAuth.getState().sessionEpoch) throw new axios.CanceledError();
  if (!data.requiresConsent || (accepted?.epoch === epoch && accepted.version === data.version)) return;
  const language = currentLanguage();
  const t = (key: Parameters<typeof translate>[1]) => translate(language, key);
  const choice = await new Promise<boolean>((resolve) => {
    const show = () => Alert.alert(t('consent.title'),
      translate(language, 'consent.body', { provider: data.provider, details: data.processingDetails }), [
        { text: t('common.cancel'), style: 'cancel', onPress: () => resolve(false) },
        { text: t('profile.privacy'), onPress: () => {
          void openLegalPage(`${data.privacyUrl}?lang=${language}`).finally(show);
        } },
        { text: t('consent.accept'), onPress: () => resolve(true) },
      ], { cancelable: false });
    show();
  });
  if (!choice) throw new axios.CanceledError(t('consent.declined'));
  if (epoch !== useAuth.getState().sessionEpoch) throw new axios.CanceledError();
  if (approve) await approve(data.version);
  else await client.put('/api/users/me/ai-consent', { version: data.version, accepted: true }, {
    headers: { Authorization: `Bearer ${useAuth.getState().accessToken}` },
  });
  if (epoch !== useAuth.getState().sessionEpoch) throw new axios.CanceledError();
  accepted = { epoch, version: data.version };
}
