import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'os-copy-button',
  imports: [IconComponent, TranslatePipe],
  templateUrl: './copy-button.component.html',
  styleUrl: './copy-button.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CopyButtonComponent {
  private readonly i18n = inject(I18nService);
  readonly value = input.required<string>();
  readonly labelKey = input('common.copy');
  readonly copied = signal(false);

  ariaLabel(): string {
    return this.i18n.translate(this.labelKey());
  }

  async copy(): Promise<void> {
    const text = this.value();
    if (!text) {
      return;
    }
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
      } else {
        fallbackCopy(text);
      }
      this.copied.set(true);
      window.setTimeout(() => this.copied.set(false), 1600);
    } catch {
      fallbackCopy(text);
      this.copied.set(true);
      window.setTimeout(() => this.copied.set(false), 1600);
    }
  }
}

function fallbackCopy(text: string): void {
  const area = document.createElement('textarea');
  area.value = text;
  area.setAttribute('readonly', '');
  area.style.position = 'fixed';
  area.style.opacity = '0';
  document.body.appendChild(area);
  area.select();
  document.execCommand('copy');
  document.body.removeChild(area);
}
