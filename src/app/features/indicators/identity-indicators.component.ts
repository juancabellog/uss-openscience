import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, map, of, startWith } from 'rxjs';
import { I18nService } from '../../core/i18n/i18n.service';
import { OrcidAdoptionMonth, OrcidAdoptionSeries, ResearcherIdentityIndicators } from '../../core/models/openscience.models';
import { ResearcherIdentityApiService } from '../../core/services/api/researcher-identity-api.service';
import { latestCompleteOrcidMonth, orcidAdoptionLinePoints } from '../../core/utils/orcid-adoption.util';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

type IdentityView =
  | { status: 'loading'; data: null; adoption: null }
  | { status: 'error'; data: null; adoption: null }
  | { status: 'success'; data: ResearcherIdentityIndicators; adoption: OrcidAdoptionSeries | null };

@Component({
  selector: 'os-identity-indicators',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    RouterLink,
    ErrorStateComponent,
    LoadingStateComponent
  ],
  templateUrl: './identity-indicators.component.html',
  styleUrl: './identity-indicators.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IdentityIndicatorsComponent {
  private readonly api = inject(ResearcherIdentityApiService);
  private readonly i18n = inject(I18nService);

  readonly view = toSignal(
    forkJoin({
      data: this.api.summary(),
      adoption: this.api.orcidAdoption().pipe(catchError(() => of<OrcidAdoptionSeries | null>(null)))
    }).pipe(
      map((result): IdentityView => ({ status: 'success', data: result.data, adoption: result.adoption })),
      catchError(() => of<IdentityView>({ status: 'error', data: null, adoption: null })),
      startWith<IdentityView>({ status: 'loading', data: null, adoption: null })
    ),
    { initialValue: { status: 'loading', data: null, adoption: null } satisfies IdentityView }
  );

  formatCount(value: number): string {
    return new Intl.NumberFormat(this.i18n.locale()).format(value);
  }

  latestMonth(series: OrcidAdoptionSeries | null): OrcidAdoptionMonth | null {
    return latestCompleteOrcidMonth(series?.months);
  }

  linePoints(series: OrcidAdoptionSeries | null): string {
    return orcidAdoptionLinePoints(series?.months);
  }
}
