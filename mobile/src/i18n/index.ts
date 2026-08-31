import { en, type Dictionary } from './en';
import { tr } from './tr';
import { useLocale } from '../store/locale';

export type Language = 'en' | 'tr';

export const LANGUAGES: readonly Language[] = ['en', 'tr'];

const DICTIONARIES: Record<Language, Dictionary> = { en, tr };

/**
 * Dotted paths into the dictionary — 'wardrobe.title', 'auth.passwordRules.length'.
 * Typing them means a renamed key breaks the build instead of rendering blank.
 */
type Leaves<T, Prefix extends string = ''> = {
  [K in keyof T & string]: T[K] extends string
    ? `${Prefix}${K}`
    : Leaves<T[K], `${Prefix}${K}.`>;
}[keyof T & string];

export type TranslationKey = Leaves<Dictionary>;

type Values = Record<string, string | number>;

function lookup(dictionary: Dictionary, key: string): string | undefined {
  const value = key
    .split('.')
    .reduce<unknown>((node, part) => (node as Record<string, unknown>)?.[part], dictionary);
  return typeof value === 'string' ? value : undefined;
}

function interpolate(template: string, values?: Values): string {
  if (!values) return template;
  return template.replace(/\{(\w+)}/g, (match, name: string) =>
    name in values ? String(values[name]) : match,
  );
}

/**
 * Resolves a key in the given language.
 *
 * English is the fallback for anything Turkish is missing, and the key itself is
 * the last resort: a screen with a gap in it still renders and stays usable.
 */
export function translate(language: Language, key: string, values?: Values): string {
  const value = lookup(DICTIONARIES[language], key) ?? lookup(en, key);
  return value === undefined ? key : interpolate(value, values);
}

/**
 * Count-aware variant. English needs a separate plural form, Turkish keeps the
 * noun singular after a numeral — so `<key>_other` simply repeats the singular
 * there, and both languages read naturally from the same call.
 */
export function translatePlural(
  language: Language,
  key: string,
  count: number,
  values?: Values,
): string {
  const pluralKey = count === 1 ? key : `${key}_other`;
  const resolved = lookup(DICTIONARIES[language], pluralKey) ?? lookup(en, pluralKey);
  return translate(language, resolved === undefined ? key : pluralKey, { count, ...values });
}

/**
 * The hook screens use. Reading the language from the store makes every screen
 * re-render when it changes, so switching language never needs an app restart.
 */
export function useT() {
  const language = useLocale((state) => state.language);
  return {
    language,
    t: (key: TranslationKey, values?: Values) => translate(language, key, values),
    tPlural: (key: TranslationKey, count: number, values?: Values) =>
      translatePlural(language, key, count, values),
  };
}

/**
 * Splits a translated sentence around one {placeholder} so the app can style the
 * middle part — the email in "we sent a code to <you@…>", for instance.
 *
 * A prefix/suffix pair of keys would not work: English puts the email in the
 * middle of the sentence and Turkish puts it first, so the split has to follow
 * the translation rather than the layout.
 */
export function splitAround(sentence: string, placeholder: string): [string, string] {
  const marker = `{${placeholder}}`;
  const at = sentence.indexOf(marker);
  return at === -1 ? [sentence, ''] : [sentence.slice(0, at), sentence.slice(at + marker.length)];
}

export { en, tr };
export type { Dictionary };
