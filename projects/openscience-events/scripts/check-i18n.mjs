import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'app', 'core', 'i18n');

function flatten(tree, prefix = '') {
  return Object.entries(tree).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return typeof value === 'string' ? [path] : flatten(value, path);
  });
}

const es = JSON.parse(readFileSync(join(root, 'es.json'), 'utf8'));
const en = JSON.parse(readFileSync(join(root, 'en.json'), 'utf8'));
const esKeys = new Set(flatten(es));
const enKeys = new Set(flatten(en));
const missingInEn = [...esKeys].filter((key) => !enKeys.has(key));
const missingInEs = [...enKeys].filter((key) => !esKeys.has(key));

if (missingInEn.length || missingInEs.length) {
  console.error('i18n check failed');
  if (missingInEn.length) {
    console.error('Missing in en:', missingInEn.join(', '));
  }
  if (missingInEs.length) {
    console.error('Missing in es:', missingInEs.join(', '));
  }
  process.exit(1);
}

const required = [
  'form.createTitle',
  'form.title',
  'form.organizingUnit',
  'adminList.manage',
  'adminList.actions',
  'adminEvent.summary',
  'form.discardChanges',
  'form.confirmFinish',
  'form.selectUnit',
  'form.otherUnit',
  'form.saveDraft',
  'form.publish',
  'form.startsAt',
  'form.blankRows',
  'nav.home',
  'register.confirm',
  'minutes.title'
];
const missingRequired = required.filter((key) => !esKeys.has(key) || !enKeys.has(key));
if (missingRequired.length) {
  console.error('Missing required keys:', missingRequired.join(', '));
  process.exit(1);
}

function normalizeLanguage(raw) {
  if (raw == null) {
    return null;
  }
  const value = String(raw).trim().toLowerCase().replace('_', '-');
  if (value === 'en' || value.startsWith('en-')) {
    return 'en';
  }
  if (value === 'es' || value.startsWith('es-')) {
    return 'es';
  }
  return null;
}

function resolveLanguage(stored, browser) {
  return normalizeLanguage(stored) ?? normalizeLanguage(browser) ?? 'es';
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(resolveLanguage('en', 'es-CL') === 'en', 'stored preference wins');
assert(resolveLanguage(null, 'en-US') === 'en', 'browser english');
assert(resolveLanguage(null, 'pt-BR') === 'es', 'unsupported browser falls back to spanish');
assert(resolveLanguage('fr', 'de') === 'es', 'unsupported stored falls back to spanish');
assert(resolveLanguage('es-CL', 'en-US') === 'es', 'normalized stored spanish');

console.log(`i18n OK: ${esKeys.size} keys in es/en`);
