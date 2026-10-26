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
const formKeys = [...esKeys].filter((key) => key.startsWith('forms.'));
const formMissing = formKeys.filter((key) => !enKeys.has(key));

if (missingInEn.length || missingInEs.length || formMissing.length) {
  console.error('i18n check failed');
  if (missingInEn.length) {
    console.error('Missing in en:', missingInEn.join(', '));
  }
  if (missingInEs.length) {
    console.error('Missing in es:', missingInEs.join(', '));
  }
  process.exit(1);
}

const requiredForm = [];
for (let i = 1; i <= 26; i += 1) {
  requiredForm.push(`forms.newProject.questions.NP${String(i).padStart(3, '0')}.label`);
}
for (let i = 1; i <= 19; i += 1) {
  requiredForm.push(`forms.validation.questions.VAL${String(i).padStart(3, '0')}.label`);
}
const missingFormLabels = requiredForm.filter((key) => !esKeys.has(key) || !enKeys.has(key));
if (missingFormLabels.length) {
  console.error('Missing form labels:', missingFormLabels.join(', '));
  process.exit(1);
}

console.log(`i18n OK: ${esKeys.size} keys in es/en, ${formKeys.length} form keys`);
