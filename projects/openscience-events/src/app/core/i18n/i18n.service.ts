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
  localeForLanguage,
  resolveLanguage
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
        console.warn(`[events-i18n] Missing translation key: ${key}`);
      }
      return '';
    }
    return interpolate(value, params);
  }

  catalog(kind: string, code?: string | null): string {
    if (!code) {
      return this.translate('common.dash');
    }
    const key = `catalogs.${kind}.${code}`;
    return this.has(key) ? this.translate(key) : code;
  }

  localizedText(original?: string | null, english?: string | null): string {
    if (this.language() === 'en' && english && english.trim()) {
      return english;
    }
    return original ?? '';
  }

  formatDateTime(value?: string | null, timeZone?: string | null): string {
    const wall = wallClock(value);
    if (!wall) {
      return value ? value : this.translate('common.dash');
    }
    const formatted = new Intl.DateTimeFormat(this.locale(), {
      dateStyle: 'short',
      timeStyle: 'short'
    }).format(wall);
    return timeZone ? `${formatted} (${timeZone})` : formatted;
  }

  formatDate(value?: string | null): string {
    const wall = wallClock(value);
    if (!wall) {
      return value ? value : this.translate('common.dash');
    }
    return new Intl.DateTimeFormat(this.locale(), { dateStyle: 'short' }).format(wall);
  }

  formatNumber(value?: number | null): string {
    if (value == null || Number.isNaN(value)) {
      return this.translate('common.dash');
    }
    return new Intl.NumberFormat(this.locale()).format(value);
  }

  formatPercent(value?: number | null): string {
    if (value == null || Number.isNaN(value)) {
      return this.translate('common.noData');
    }
    return new Intl.NumberFormat(this.locale(), { style: 'percent', maximumFractionDigits: 1 }).format(value / 100);
  }

  errorKey(err: unknown, fallbackKey: string): string {
    const code = readErrorCode(err);
    if (code && this.has(`errors.${code}`)) {
      return `errors.${code}`;
    }
    return fallbackKey;
  }

  private applyDocumentLanguage(language: AppLanguage): void {
    if (typeof document === 'undefined') {
      return;
    }
    document.documentElement.lang = localeForLanguage(language);
    document.title = this.translate('brand.documentTitle');
  }
}

export function resolveInitialLanguage(): AppLanguage {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem(LANGUAGE_STORAGE_KEY) : null;
  const browser = typeof navigator !== 'undefined' ? navigator.language : null;
  return resolveLanguage(stored, browser);
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

function wallClock(value?: string | null): Date | null {
  if (!value) {
    return null;
  }
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))/);
  if (!match) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  return new Date(
    Number(match[1]),
    Number(match[2]) - 1,
    Number(match[3]),
    Number(match[4] ?? '0'),
    Number(match[5] ?? '0')
  );
}

function readErrorCode(err: unknown): string | null {
  if (err && typeof err === 'object' && 'error' in err) {
    const payload = (err as { error?: { code?: string } }).error;
    return payload?.code ?? null;
  }
  return null;
}
