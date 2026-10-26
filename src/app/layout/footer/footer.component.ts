import { ChangeDetectionStrategy, Component } from '@angular/core';
import { I18N_HOST } from '../../shared/i18n/i18n.imports';
import { TranslatePipe } from '../../shared/pipes/translate.pipe';

@Component({
  selector: 'os-footer',
  hostDirectives: [...I18N_HOST],
  imports: [TranslatePipe],
  templateUrl: './footer.component.html',
  styleUrl: './footer.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FooterComponent {}
