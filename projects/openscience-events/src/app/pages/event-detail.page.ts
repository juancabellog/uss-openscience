import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';
import { EventPublicDetail, EventsApiService } from '../core/events-api.service';
import { I18nService } from '../core/i18n/i18n.service';
import { PublicEventView, publicEventFailure } from '../core/public-event-state';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-event-detail-page',
  imports: [RouterLink, ...I18N_IMPORTS],
  templateUrl: './event-detail.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EventDetailPage {
  private readonly api = inject(EventsApiService);
  private readonly i18n = inject(I18nService);
  readonly slug = input.required<string>();
  readonly view = toSignal(
    toObservable(this.slug).pipe(
      switchMap((slug) =>
        this.api.getBySlug(slug).pipe(
          map((event): PublicEventView => ({ kind: 'ok', event })),
          catchError((error: unknown) => of(publicEventFailure(error)))
        )
      )
    ),
    { initialValue: { kind: 'loading', event: null } as PublicEventView }
  );

  titleOf(event: EventPublicDetail): string {
    return this.i18n.localizedText(event.title, event.titleEn);
  }

  descriptionOf(event: EventPublicDetail): string {
    return this.i18n.localizedText(event.description, event.descriptionEn);
  }

  unitOf(event: EventPublicDetail): string {
    return this.i18n.localizedText(event.organizingUnit, event.organizingUnitEn);
  }

  statusKey(event: EventPublicDetail): string {
    if (event.publicationStatus === 'CANCELLED') {
      return 'detail.cancelled';
    }
    if (event.publicationStatus === 'FINISHED') {
      return 'detail.finished';
    }
    return event.registrationOpen ? 'detail.registrationOpen' : 'detail.registrationClosed';
  }
}
