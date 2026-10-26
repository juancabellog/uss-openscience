import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { I18nService } from '../../../core/i18n/i18n.service';

@Component({
  selector: 'os-error-state',
  templateUrl: './error-state.component.html',
  styleUrl: './error-state.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ErrorStateComponent {
  private readonly i18n = inject(I18nService);
  readonly title = input('');
  readonly message = input('');
  readonly actionLabel = input('');
  readonly action = output<void>();
  readonly displayTitle = computed(() => this.title() || this.i18n.translate('common.errorTitle'));
  readonly displayMessage = computed(() => this.message() || this.i18n.translate('common.errorMessage'));
}
