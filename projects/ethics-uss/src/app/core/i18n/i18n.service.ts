import { ApplicationRef, Injectable, computed, inject, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
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
  private readonly language = signal<AppLanguage>(resolveInitialLanguage());

  readonly currentLanguage = this.language.asReadonly();
  readonly locale = computed(() => localeForLanguage(this.language()));

  constructor() {
    this.applyDocumentLanguage(this.language());
  }

  setLanguage(language: AppLanguage): void {
    if (!APP_LANGUAGES.includes(language)) {
      return;
    }
    if (language === this.language()) {
      this.applyDocumentLanguage(language);
      return;
    }
    this.language.set(language);
    persistLanguage(language);
    this.applyDocumentLanguage(language);
    queueMicrotask(() => this.appRef.tick());
  }

  has(key: string): boolean {
    return lookup(CATALOGS[this.language()], key) !== undefined || lookup(CATALOGS[DEFAULT_LANGUAGE], key) !== undefined;
  }

  translate(key: string, params?: Record<string, string | number>): string {
    const value = lookup(CATALOGS[this.language()], key) ?? lookup(CATALOGS[DEFAULT_LANGUAGE], key);
    if (value === undefined) {
      if (!environment.production) {
        console.warn(`[ethics-i18n] Missing translation key: ${key}`);
      }
      return '';
    }
    return interpolate(value, params);
  }

  notificationText(item: { type?: string; title: string; body: string; params?: Record<string, string | number> }): { title: string; body: string } {
    if (item.type && this.has(`notifications.${item.type}.title`)) {
      return {
        title: this.translate(`notifications.${item.type}.title`, item.params),
        body: this.translate(`notifications.${item.type}.body`, item.params)
      };
    }
    return { title: item.title, body: item.body };
  }

  auditAction(action: string, fallback: string): string {
    const key = `audit.actions.${action}`;
    return this.has(key) ? this.translate(key) : fallback;
  }

  catalog(kind: string, code?: string | null): string {
    if (!code) {
      return this.translate('common.dash');
    }
    const key = `catalogs.${kind}.${code}`;
    return this.has(key) ? this.translate(key) : code;
  }

  formatDate(value?: string | null, style: 'short' | 'long' = 'short'): string {
    if (!value) {
      return this.translate('common.dash');
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return new Intl.DateTimeFormat(this.locale(), style === 'long'
      ? { day: 'numeric', month: 'long', year: 'numeric' }
      : { day: '2-digit', month: '2-digit', year: 'numeric' }
    ).format(date);
  }

  private applyDocumentLanguage(language: AppLanguage): void {
    if (typeof document === 'undefined') {
      return;
    }
    document.documentElement.lang = language;
  }
}

function resolveInitialLanguage(): AppLanguage {
  if (typeof localStorage !== 'undefined') {
    const stored = localStorage.getItem(LANGUAGE_STORAGE_KEY);
    if (stored === 'en' || stored === 'es') {
      return stored;
    }
  }
  if (typeof navigator !== 'undefined') {
    const browser = navigator.language.toLowerCase();
    if (browser.startsWith('es')) {
      return 'es';
    }
    if (browser.startsWith('en')) {
      return 'en';
    }
  }
  return DEFAULT_LANGUAGE;
}

function persistLanguage(language: AppLanguage): void {
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
  }
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
