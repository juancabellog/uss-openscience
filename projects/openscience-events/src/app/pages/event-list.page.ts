import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { EventPublicSummary, EventsApiService } from '../core/events-api.service';
import { I18nService } from '../core/i18n/i18n.service';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-event-list-page',
  imports: [RouterLink, ...I18N_IMPORTS],
  templateUrl: './event-list.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EventListPage {
  private readonly api = inject(EventsApiService);
  private readonly i18n = inject(I18nService);
  readonly view = toSignal(
    this.api.catalog().pipe(
      map((catalog) => ({ state: 'ok' as const, catalog })),
      catchError(() => of({ state: 'error' as const, catalog: null })),
      startWith({ state: 'loading' as const, catalog: null })
    ),
    { initialValue: { state: 'loading' as const, catalog: null } }
  );

  titleOf(event: EventPublicSummary): string {
    return this.i18n.localizedText(event.title, event.titleEn);
  }
}
