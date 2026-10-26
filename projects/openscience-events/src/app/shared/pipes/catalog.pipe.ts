import { Pipe, PipeTransform, inject } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';

@Pipe({
  name: 'catalog',
  pure: false
})
export class CatalogPipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(code?: string | null, kind = 'statuses'): string {
    return this.i18n.catalog(kind, code);
  }
}
