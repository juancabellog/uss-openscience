export type AppLanguage = 'es' | 'en';

export type TranslationTree = {
  [key: string]: string | TranslationTree;
};

export const APP_LANGUAGES: readonly AppLanguage[] = ['es', 'en'];
export const DEFAULT_LANGUAGE: AppLanguage = 'es';
export const LANGUAGE_STORAGE_KEY = 'openscience-events.language';

export function localeForLanguage(language: AppLanguage): string {
  return language === 'en' ? 'en-US' : 'es-CL';
}

export function normalizeLanguage(raw?: string | null): AppLanguage | null {
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

export function resolveLanguage(stored?: string | null, browser?: string | null): AppLanguage {
  return normalizeLanguage(stored) ?? normalizeLanguage(browser) ?? DEFAULT_LANGUAGE;
}

export function flattenKeys(tree: TranslationTree, prefix = ''): string[] {
  return Object.entries(tree).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return typeof value === 'string' ? [path] : flattenKeys(value, path);
  });
}
