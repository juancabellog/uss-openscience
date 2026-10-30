import { ChangeDetectionStrategy, Component, ElementRef, input, output, viewChild } from '@angular/core';
import { IconComponent } from '../../shared/components/icon.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-document-upload-action',
  imports: [IconComponent, ...I18N_IMPORTS],
  template: `
    <input
      #picker
      class="file-input"
      type="file"
      accept="application/pdf"
      tabindex="-1"
      [attr.aria-label]="accessibleName()"
      (change)="choose($event)"
    />
    @if (busy()) {
      <button type="button" class="ghost" [class.labeled]="compact()" disabled [attr.aria-busy]="true" [attr.aria-label]="accessibleName()">
        <span class="spinner" aria-hidden="true"></span>
        <span>{{ 'projects.uploading' | translate }}</span>
      </button>
    } @else if (error()) {
      <p class="upload-error" role="alert">{{ error() }}</p>
      <button type="button" class="ghost" [attr.aria-label]="accessibleName()" (click)="open()">
        {{ 'projects.retry' | translate }}
      </button>
    } @else {
      <button
        type="button"
        class="ghost"
        [class.labeled]="compact()"
        [disabled]="disabled()"
        [attr.aria-label]="accessibleName()"
        [attr.data-tooltip]="tooltipKey() ? (tooltipKey() | translate) : null"
        (click)="open()"
      >
        <eth-icon name="upload" />
        <span>{{ labelKey() | translate }}</span>
      </button>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem;
    }
    .file-input {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }
    .upload-error {
      margin: 0;
      color: var(--eth-danger);
      font-size: 0.85rem;
    }
    .spinner {
      width: 0.85rem;
      height: 0.85rem;
      border: 2px solid var(--eth-border);
      border-top-color: var(--eth-primary);
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
    }
    @media (prefers-reduced-motion: reduce) {
      .spinner {
        animation: none;
        border-top-color: var(--eth-primary);
      }
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DocumentUploadActionComponent {
  readonly labelKey = input.required<string>();
  readonly tooltipKey = input<string | null>(null);
  readonly compact = input(false);
  readonly accessibleName = input.required<string>();
  readonly disabled = input(false);
  readonly busy = input(false);
  readonly error = input('');
  readonly selected = output<File>();
  private readonly picker = viewChild<ElementRef<HTMLInputElement>>('picker');

  open(): void {
    this.picker()?.nativeElement.click();
  }

  choose(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) {
      this.selected.emit(file);
    }
  }
}
