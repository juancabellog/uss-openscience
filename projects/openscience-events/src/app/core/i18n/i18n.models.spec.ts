import { resolveLanguage } from './i18n.models';

function assert(condition: unknown, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

export function runI18nModelTests(): void {
  assert(resolveLanguage('en', 'es-CL') === 'en', 'stored preference wins');
  assert(resolveLanguage(null, 'en-US') === 'en', 'browser english');
  assert(resolveLanguage(null, 'pt-BR') === 'es', 'unsupported browser falls back to spanish');
  assert(resolveLanguage('zh-CN', 'de-DE') === 'es', 'unsupported stored and browser');
  assert(resolveLanguage('es-CL', 'en-US') === 'es', 'normalized stored spanish');
  assert(resolveLanguage('en_US', null) === 'en', 'underscore english');
}

runI18nModelTests();
