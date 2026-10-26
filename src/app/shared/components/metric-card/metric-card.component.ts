import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { I18N_HOST } from '../../i18n/i18n.imports';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { InfoHintComponent } from '../info-hint/info-hint.component';

@Component({
  selector: 'os-metric-card',
  hostDirectives: [...I18N_HOST],
  imports: [TranslatePipe, InfoHintComponent],
  templateUrl: './metric-card.component.html',
  styleUrl: './metric-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MetricCardComponent {
  readonly label = input.required<string>();
  readonly value = input.required<string>();
  readonly hint = input('');
  readonly detail = input('');
  readonly tooltip = input('');
  readonly infoText = input('');
  readonly infoAriaLabel = input('');
  readonly demo = input(false);
}
