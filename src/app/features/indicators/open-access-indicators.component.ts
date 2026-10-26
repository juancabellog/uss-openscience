import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { UnpaywallStats } from '../../core/models/openscience.models';
import { I18nService } from '../../core/i18n/i18n.service';
import { UnpaywallApiService } from '../../core/services/api/unpaywall-api.service';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

type OpenAccessView =
  | { status: 'loading'; data: null }
  | { status: 'error'; data: null }
  | { status: 'success'; data: UnpaywallStats };

@Component({
  selector: 'os-open-access-indicators',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, RouterLink, ErrorStateComponent, LoadingStateComponent],
  templateUrl: './open-access-indicators.component.html',
  styleUrl: './identity-indicators.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OpenAccessIndicatorsComponent {
  private readonly api = inject(UnpaywallApiService);
  private readonly i18n = inject(I18nService);

  readonly view = toSignal(
    this.api.stats().pipe(
      map((data): OpenAccessView => ({ status: 'success', data })),
      catchError(() => of<OpenAccessView>({ status: 'error', data: null })),
      startWith<OpenAccessView>({ status: 'loading', data: null })
    ),
    { initialValue: { status: 'loading', data: null } satisfies OpenAccessView }
  );

  formatCount(value: number | null | undefined): string {
    return new Intl.NumberFormat(this.i18n.locale()).format(value ?? 0);
  }

  percent(numerator: number, denominator: number): number {
    if (!denominator) {
      return 0;
    }
    return (numerator * 100) / denominator;
  }
}
