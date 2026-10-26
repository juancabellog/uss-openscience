import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { I18nService } from '../../../core/i18n/i18n.service';

@Component({
  selector: 'os-demo-banner',
  templateUrl: './demo-banner.component.html',
  styleUrl: './demo-banner.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DemoBannerComponent {
  private readonly i18n = inject(I18nService);
  readonly text = input('');
  readonly displayText = computed(() => this.text() || this.i18n.translate('common.demoDefault'));
}
