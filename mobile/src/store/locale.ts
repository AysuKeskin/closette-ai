import { getLocales } from 'expo-localization';
import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

import type { Language } from '../i18n';

const LANGUAGE_KEY = 'closette_language';

/** The device's language if we ship it, English otherwise. */
export function deviceLanguage(): Language {
  const code = getLocales()[0]?.languageCode?.toLowerCase();
  return code === 'tr' ? 'tr' : 'en';
}

type LocaleState = {
  language: Language;
  /** False until the stored choice has been read, so nothing flashes in the wrong language. */
  hydrated: boolean;
  /** True while the device language is in use — no explicit choice has been made. */
  followsDevice: boolean;
  hydrate: () => Promise<void>;
  setLanguage: (language: Language) => Promise<void>;
  useDeviceLanguage: () => Promise<void>;
};

export const useLocale = create<LocaleState>((set) => ({
  language: deviceLanguage(),
  hydrated: false,
  followsDevice: true,

  hydrate: async () => {
    try {
      const stored = await SecureStore.getItemAsync(LANGUAGE_KEY);
      if (stored === 'en' || stored === 'tr') {
        set({ language: stored, followsDevice: false, hydrated: true });
        return;
      }
    } catch {
      // A locked keychain must not keep the app in the wrong language forever.
    }
    set({ language: deviceLanguage(), followsDevice: true, hydrated: true });
  },

  setLanguage: async (language) => {
    set({ language, followsDevice: false });
    try {
      await SecureStore.setItemAsync(LANGUAGE_KEY, language);
    } catch {
      // Persisting is best-effort; the app is already in the chosen language.
    }
  },

  useDeviceLanguage: async () => {
    set({ language: deviceLanguage(), followsDevice: true });
    try {
      await SecureStore.deleteItemAsync(LANGUAGE_KEY);
    } catch {
      // Same as above.
    }
  },
}));

/** For non-React callers (the API client's request interceptor). */
export function currentLanguage(): Language {
  return useLocale.getState().language;
}
