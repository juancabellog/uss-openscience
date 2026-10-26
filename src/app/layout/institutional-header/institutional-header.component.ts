import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { I18N_HOST } from '../../shared/i18n/i18n.imports';
import { TranslatePipe } from '../../shared/pipes/translate.pipe';

@Component({
  selector: 'os-institutional-header',
  hostDirectives: [...I18N_HOST],
  imports: [RouterLink, TranslatePipe],
  templateUrl: './institutional-header.component.html',
  styleUrl: './institutional-header.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class InstitutionalHeaderComponent {
  readonly ussLogoUrl = environment.ussLogoUrl;
  readonly openscienceLogoUrl = environment.brandingLogoUrl;
}
