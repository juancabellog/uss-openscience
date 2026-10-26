import { Pipe, PipeTransform, inject } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';

@Pipe({
  name: 'localeDate',
  pure: false
})
export class LocaleDatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(
    value: string | Date | null | undefined,
    preset: 'datetime' | 'date' = 'datetime'
  ): string {
    if (!value) {
      return '';
    }

    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }

    const options: Intl.DateTimeFormatOptions =
      preset === 'date'
        ? { dateStyle: 'short' }
        : { dateStyle: 'short', timeStyle: 'short' };

    return new Intl.DateTimeFormat(this.i18n.locale(), options).format(date);
  }
}
