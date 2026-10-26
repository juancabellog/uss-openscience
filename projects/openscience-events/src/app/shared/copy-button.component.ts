import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';
import { I18N_IMPORTS } from './i18n/i18n.imports';

@Component({
  selector: 'ose-copy-button',
  imports: [...I18N_IMPORTS],
  template: `
    <button
      type="button"
      class="copy-action"
      [class.secondary]="variant() === 'secondary'"
      [class.linkish]="variant() === 'linkish'"
      [class.is-copied]="copied()"
      (click)="copy()"
    >
      <span class="copy-icon" aria-hidden="true">
        @if (copied()) {
          <svg viewBox="0 0 16 16" width="14" height="14" focusable="false">
            <path fill="currentColor" d="M6.2 11.4 2.8 8l1.1-1.1 2.3 2.3 5.9-5.9L13.2 4z" />
          </svg>
        } @else {
          <svg viewBox="0 0 16 16" width="14" height="14" focusable="false">
            <path fill="currentColor" d="M6 2h5l3 3v8H6zM5 4H3v11h8v-2H5z" />
          </svg>
        }
      </span>
      @if (copied()) {
        {{ 'common.copied' | translate }}
      } @else {
        <ng-content />
      }
    </button>
    <span class="sr-only" aria-live="polite">{{ live() }}</span>
    @if (failed()) {
      <span class="copy-error" role="alert">{{ 'common.copyFailed' | translate }}</span>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      flex-direction: column;
      align-items: stretch;
    }
    .copy-action.linkish {
      min-width: 0;
      justify-content: flex-start;
      height: auto;
      padding: 0;
      background: none;
      color: var(--uss-primary);
      font-weight: 600;
    }
    .copy-icon {
      display: inline-flex;
      width: 1rem;
      height: 1rem;
    }
    .copy-error {
      margin-top: 0.35rem;
      color: var(--ose-danger);
      font-size: 0.85rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CopyButtonComponent {
  private readonly i18n = inject(I18nService);
  readonly value = input<string | null>(null);
  readonly variant = input<'secondary' | 'linkish'>('secondary');
  readonly copied = signal(false);
  readonly failed = signal(false);
  readonly live = signal('');
  private timer: ReturnType<typeof setTimeout> | null = null;

  async copy(): Promise<void> {
    const text = this.value()?.trim();
    if (!text) {
      return;
    }
    this.clearTimer();
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set(true);
      this.failed.set(false);
      this.live.set(this.i18n.translate('common.copied'));
      this.timer = setTimeout(() => {
        this.copied.set(false);
        this.live.set('');
        this.timer = null;
      }, 2000);
    } catch {
      this.copied.set(false);
      this.failed.set(true);
      this.live.set(this.i18n.translate('common.copyFailed'));
    }
  }

  private clearTimer(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
