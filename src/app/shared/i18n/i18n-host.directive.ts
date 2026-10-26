import { Directive, inject } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';

@Directive({
  selector: '[osI18nHost]',
  host: {
    '[attr.data-lang]': 'lang()'
  }
})
export class I18nHostDirective {
  readonly lang = inject(I18nService).currentLanguage;
}
