import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CopyButtonComponent } from '../copy-button/copy-button.component';
import { IconComponent } from '../icon/icon.component';
import { I18N_HOST, I18N_IMPORTS } from '../../i18n/i18n.imports';

@Component({
  selector: 'os-identifier-row',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, CopyButtonComponent, IconComponent],
  templateUrl: './identifier-row.component.html',
  styleUrl: './identifier-row.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IdentifierRowComponent {
  readonly label = input.required<string>();
  readonly value = input<string | number | null>(null);
  readonly href = input<string | null>(null);
  readonly copyLabelKey = input('common.copy');
  readonly tooltip = input('');
  readonly external = input(false);

  displayValue(): string {
    const value = this.value();
    return value == null || value === '' ? '' : String(value);
  }
}
