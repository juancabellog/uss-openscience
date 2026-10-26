import { Pipe, PipeTransform, inject } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';

@Pipe({
  name: 'localeDate',
  pure: false
})
export class LocaleDatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(value?: string | null, style: 'short' | 'long' = 'short'): string {
    return this.i18n.formatDate(value, style);
  }
}
