#!/usr/bin/env node
/**
 * Checks the two mobile dictionaries against each other, and against the app.
 *
 * TypeScript already guarantees the *keys* match — `tr` is typed as the full
 * Dictionary. What it cannot see is the inside of a string: a placeholder that
 * only exists in one language renders as a literal "{count}", and a Turkish line
 * left as its English original is a translation nobody did.
 *
 * It also cannot see a key nobody renders. That is how a "Device language"
 * option once sat translated-but-unreachable in both bundles, so unused keys
 * fail here too.
 *
 * Run: node scripts/check-i18n.mjs
 */
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, sep } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const PLACEHOLDER = /\{(\w+)}/g;

/** Values a translator legitimately leaves alone: loanwords and brand names. */
const ALLOWED_IDENTICAL = new Set([
  'language.english',
  'language.turkish',
  'auth.codePlaceholder',
  'auth.passwordPlaceholder',
  'aesthetics.cleangirl',
  'aesthetics.oldmoney',
  'aesthetics.coquette',
  'aesthetics.minimalist',
  'confirm.pattern',
  'beautyForm.brandPlaceholderOrdinary',
  'beautyForm.brandPlaceholderCerave',
]);

/**
 * Key prefixes the app builds at runtime rather than writing out in full —
 * `t(rule.label)`, `t(item.nameKey)`. A scan of the source cannot see these, so
 * they are exempt from the unused-key check. Keep this list short: every entry
 * is a place the checker is blind.
 */
const DYNAMIC_PREFIXES = [
  'aesthetics.',
  'auth.passwordRules.',
  'auth.passwordStrength.',
  'getReady.suggestions.',
  'shop.verdict.',
  'style.dressUp.',
  'tabs.',
];

/** Pull the object literal out of a `.ts` dictionary without compiling it. */
function loadDictionary(file, exportName) {
  const source = readFileSync(join(here, '..', 'src', 'i18n', file), 'utf8');
  const start = source.indexOf('{', source.indexOf(exportName));
  let depth = 0;
  let end = start;
  for (; end < source.length; end++) {
    if (source[end] === '{') depth++;
    if (source[end] === '}' && --depth === 0) break;
  }
  const literal = source
    .slice(start, end + 1)
    .replace(/\/\/[^\n]*/g, '')
    .replace(/,(\s*[}\]])/g, '$1');
  // eslint-disable-next-line no-new-func -- the input is our own source file
  return new Function(`return (${literal});`)();
}

/** Every .ts/.tsx source in the app, concatenated, minus the dictionaries. */
function readSources(dir, out = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) readSources(path, out);
    else if (/\.tsx?$/.test(entry.name) && !path.includes(`${sep}i18n${sep}`)) {
      out.push(readFileSync(path, 'utf8'));
    }
  }
  return out.join('\n');
}

function flatten(node, prefix = '', out = {}) {
  for (const [key, value] of Object.entries(node)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof value === 'string') out[path] = value;
    else flatten(value, path, out);
  }
  return out;
}

const en = flatten(loadDictionary('en.ts', 'export const en'));
const tr = flatten(loadDictionary('tr.ts', 'export const tr'));
const problems = [];

for (const key of Object.keys(en)) {
  if (!(key in tr)) {
    problems.push(`missing in tr: ${key}`);
    continue;
  }
  const inEnglish = [...en[key].matchAll(PLACEHOLDER)].map((m) => m[1]).sort();
  const inTurkish = [...tr[key].matchAll(PLACEHOLDER)].map((m) => m[1]).sort();
  if (inEnglish.join(',') !== inTurkish.join(',')) {
    problems.push(`placeholders differ: ${key} — en {${inEnglish}} vs tr {${inTurkish}}`);
  }
  if (en[key] === tr[key] && !ALLOWED_IDENTICAL.has(key)) {
    problems.push(`untranslated: ${key} — "${en[key]}"`);
  }
  if (!tr[key].trim()) problems.push(`empty translation: ${key}`);
}
for (const key of Object.keys(tr)) {
  if (!(key in en)) problems.push(`only in tr: ${key}`);
}

// Unused keys: dead copy that still has to be translated and reviewed.
const sources = readSources(join(here, '..', 'src'));
for (const key of Object.keys(en)) {
  if (DYNAMIC_PREFIXES.some((prefix) => key.startsWith(prefix))) continue;
  // Plural keys are reached through their base: tPlural('x.y', n) → 'x.y_other'.
  const base = key.endsWith('_other') ? key.slice(0, -'_other'.length) : key;
  if (!sources.includes(`'${base}'`)) problems.push(`unused: ${key}`);
}

if (problems.length > 0) {
  console.error(`i18n check failed (${problems.length}):`);
  for (const problem of problems) console.error(`  - ${problem}`);
  process.exit(1);
}
console.log(`i18n ok — ${Object.keys(en).length} keys in both languages`);
