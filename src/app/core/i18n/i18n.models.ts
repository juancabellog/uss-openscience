export type AppLanguage = 'es' | 'en';

export type TranslationTree = {
  [key: string]: string | TranslationTree;
};

export const APP_LANGUAGES: readonly AppLanguage[] = ['es', 'en'];
export const DEFAULT_LANGUAGE: AppLanguage = 'es';
export const LANGUAGE_STORAGE_KEY = 'openscience.language';

export function localeForLanguage(language: AppLanguage): string {
  return language === 'en' ? 'en-US' : 'es-CL';
}
