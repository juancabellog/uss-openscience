import { ChangeDetectionStrategy, Component } from '@angular/core';
import { environment } from '../../../environments/environment';
import { I18N_HOST } from '../../shared/i18n/i18n.imports';
import { TranslatePipe } from '../../shared/pipes/translate.pipe';

@Component({
  selector: 'os-environment-badge',
  hostDirectives: [...I18N_HOST],
  imports: [TranslatePipe],
  templateUrl: './environment-badge.component.html',
  styleUrl: './environment-badge.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EnvironmentBadgeComponent {
  readonly label = environment.environmentBadge;
  readonly kind = environment.environmentKind;
}
