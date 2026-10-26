export type AppLanguage = 'es' | 'en';

export type TranslationTree = {
  [key: string]: string | TranslationTree;
};

export const APP_LANGUAGES: readonly AppLanguage[] = ['es', 'en'];
export const DEFAULT_LANGUAGE: AppLanguage = 'es';
export const LANGUAGE_STORAGE_KEY = 'ethics-uss.language';

export function localeForLanguage(language: AppLanguage): string {
  return language === 'en' ? 'en-US' : 'es-CL';
}

export function flattenKeys(tree: TranslationTree, prefix = ''): string[] {
  return Object.entries(tree).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return typeof value === 'string' ? [path] : flattenKeys(value, path);
  });
}
