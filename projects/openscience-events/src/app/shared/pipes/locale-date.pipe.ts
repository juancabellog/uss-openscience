import { Pipe, PipeTransform, inject } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';

@Pipe({
  name: 'localeDate',
  pure: false
})
export class LocaleDatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(value?: string | null, timeZone?: string | null): string {
    return this.i18n.formatDateTime(value, timeZone);
  }
}
