import { ApplicationRef, Injectable, computed, inject, signal } from '@angular/core';
import en from './en.json';
import es from './es.json';
import {
  APP_LANGUAGES,
  AppLanguage,
  DEFAULT_LANGUAGE,
  LANGUAGE_STORAGE_KEY,
  TranslationTree,
  localeForLanguage
} from './i18n.models';

const CATALOGS: Record<AppLanguage, TranslationTree> = {
  es: es as TranslationTree,
  en: en as TranslationTree
};

@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly appRef = inject(ApplicationRef);
  private readonly language = signal<AppLanguage>(readStoredLanguage());

  readonly currentLanguage = this.language.asReadonly();
  readonly locale = computed(() => localeForLanguage(this.language()));

  constructor() {
    this.applyDocumentLanguage(this.language());
  }

  applyRequestedLanguage(raw: string | null | undefined): void {
    const value = (raw ?? '').trim().toLowerCase();
    if (value === 'en' || value.startsWith('en-')) {
      this.setLanguage('en');
      return;
    }
    if (value === 'es' || value.startsWith('es-')) {
      this.setLanguage('es');
    }
  }

  setLanguage(language: AppLanguage): void {
    if (!APP_LANGUAGES.includes(language) || language === this.language()) {
      this.applyDocumentLanguage(language);
      return;
    }

    this.language.set(language);
    persistLanguage(language);
    this.applyDocumentLanguage(language);
    queueMicrotask(() => this.appRef.tick());
  }

  translate(key: string, params?: Record<string, string | number>): string {
    const value = lookup(CATALOGS[this.language()], key) ?? lookup(CATALOGS[DEFAULT_LANGUAGE], key);
    if (value === undefined) {
      return key;
    }
    return interpolate(value, params);
  }

  private applyDocumentLanguage(language: AppLanguage): void {
    if (typeof document === 'undefined') {
      return;
    }
    document.documentElement.lang = language;
  }
}

function readStoredLanguage(): AppLanguage {
  if (typeof localStorage === 'undefined') {
    return DEFAULT_LANGUAGE;
  }

  const stored = localStorage.getItem(LANGUAGE_STORAGE_KEY);
  return stored === 'en' || stored === 'es' ? stored : DEFAULT_LANGUAGE;
}

function persistLanguage(language: AppLanguage): void {
  if (typeof localStorage === 'undefined') {
    return;
  }
  localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
}

function lookup(tree: TranslationTree, key: string): string | undefined {
  const parts = key.split('.');
  let node: string | TranslationTree | undefined = tree;

  for (const part of parts) {
    if (typeof node !== 'object' || node === null || !(part in node)) {
      return undefined;
    }
    node = node[part];
  }

  return typeof node === 'string' ? node : undefined;
}

function interpolate(template: string, params?: Record<string, string | number>): string {
  if (!params) {
    return template;
  }
  return template.replace(/\{(\w+)\}/g, (match, token: string) => {
    const value = params[token];
    return value === undefined ? match : String(value);
  });
}
