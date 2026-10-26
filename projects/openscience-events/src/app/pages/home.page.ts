import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { EventsApiService } from '../core/events-api.service';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-home-page',
  imports: [RouterLink, ...I18N_IMPORTS],
  templateUrl: './home.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HomePage {
  private readonly api = inject(EventsApiService);
  readonly connection = toSignal(
    this.api.health().pipe(
      map((status) => ({ state: 'ok' as const, status })),
      catchError(() => of({ state: 'error' as const, status: null })),
      startWith({ state: 'loading' as const, status: null })
    ),
    { initialValue: { state: 'loading' as const, status: null } }
  );
}
