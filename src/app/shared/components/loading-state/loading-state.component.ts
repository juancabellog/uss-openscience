import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { I18nService } from '../../../core/i18n/i18n.service';

@Component({
  selector: 'os-loading-state',
  templateUrl: './loading-state.component.html',
  styleUrl: './loading-state.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoadingStateComponent {
  private readonly i18n = inject(I18nService);
  readonly label = input('');
  readonly displayLabel = computed(() => this.label() || this.i18n.translate('common.loading'));
}
