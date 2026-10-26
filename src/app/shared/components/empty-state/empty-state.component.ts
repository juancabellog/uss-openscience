import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { I18nService } from '../../../core/i18n/i18n.service';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'os-empty-state',
  imports: [IconComponent],
  templateUrl: './empty-state.component.html',
  styleUrl: './empty-state.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EmptyStateComponent {
  private readonly i18n = inject(I18nService);
  readonly title = input('');
  readonly message = input('');
  readonly icon = input('');
  readonly actionLabel = input('');
  readonly actionVariant = input<'primary' | 'link' | 'secondary'>('secondary');
  readonly action = output<void>();
  readonly displayTitle = computed(() => this.title() || this.i18n.translate('common.emptyTitle'));
  readonly displayMessage = computed(() => this.message() || this.i18n.translate('common.emptyMessage'));
}
