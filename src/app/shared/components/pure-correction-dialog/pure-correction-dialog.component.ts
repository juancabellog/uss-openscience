import { ChangeDetectionStrategy, Component, ElementRef, effect, input, output, viewChild } from '@angular/core';
import { I18N_HOST, I18N_IMPORTS } from '../../i18n/i18n.imports';
import { IconComponent } from '../icon/icon.component';

export type PureCorrectionKind = 'scopus' | 'doi';

@Component({
  selector: 'os-pure-correction-dialog',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, IconComponent],
  templateUrl: './pure-correction-dialog.component.html',
  styleUrl: './pure-correction-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PureCorrectionDialogComponent {
  readonly kind = input.required<PureCorrectionKind>();
  readonly open = input(false);
  readonly busy = input(false);
  readonly parentName = input<string | null>(null);
  readonly value = input<string | null>(null);
  readonly childPureId = input<number | null>(null);
  readonly accessLabel = input<string | null>(null);
  readonly versionLabel = input<string | null>(null);
  readonly panel = viewChild<ElementRef<HTMLElement>>('panel');

  readonly cancelled = output<void>();
  readonly confirmed = output<void>();

  constructor() {
    effect(() => {
      if (this.open()) {
        queueMicrotask(() => this.panel()?.nativeElement.focus());
      }
    });
  }

  titleKey(): string {
    return this.kind() === 'scopus'
      ? 'pureCorrection.scopusTitle'
      : 'pureCorrection.doiTitle';
  }

  cancel(): void {
    if (!this.busy()) {
      this.cancelled.emit();
    }
  }

  confirm(): void {
    if (!this.busy()) {
      this.confirmed.emit();
    }
  }
}
