import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { I18nService } from '../../../core/i18n/i18n.service';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'os-search-box',
  imports: [ReactiveFormsModule, IconComponent],
  templateUrl: './search-box.component.html',
  styleUrl: './search-box.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SearchBoxComponent {
  private readonly i18n = inject(I18nService);
  readonly control = input.required<FormControl<string>>();
  readonly placeholder = input('');
  readonly label = input('');
  readonly hint = input('');
  readonly displayPlaceholder = computed(() => this.placeholder() || this.i18n.translate('common.search'));
  readonly displayLabel = computed(() => this.label() || this.i18n.translate('common.search'));
}
