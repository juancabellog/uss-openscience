import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { CrossrefWorkSummary, DspaceItemSummary, ResearchOutputDetailItem, ResearchOutputMetrics } from '../../core/models/openscience.models';
import { localizedPureTerm, workflowStepCode } from '../../core/utils/localized-pure-term.util';
import { metricFetchKind, metricFetchMessageKey, observationDisplayValue } from '../../core/utils/pure-metrics.util';
import { I18nService } from '../../core/i18n/i18n.service';
import { CrossrefApiService } from '../../core/services/api/crossref-api.service';
import { DspaceApiService } from '../../core/services/api/dspace-api.service';
import { ResearchOutputApiService } from '../../core/services/api/research-output-api.service';
import { DetailSectionNavComponent, DetailSectionNavItem } from '../../shared/components/detail-section-nav/detail-section-nav.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { IdentifierRowComponent } from '../../shared/components/identifier-row/identifier-row.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

export const RESEARCH_OUTPUT_DETAIL_SECTIONS: DetailSectionNavItem[] = [
  { id: 'general', labelKey: 'outputs.general' },
  { id: 'metricas', labelKey: 'metrics.title' },
  { id: 'identificadores', labelKey: 'outputs.identifiers' },
  { id: 'publicacion', labelKey: 'outputs.publication' },
  { id: 'contributors', labelKey: 'outputs.contributors' },
  { id: 'versiones', labelKey: 'outputs.electronicVersions' },
  { id: 'keywords', labelKey: 'outputs.keywords' },
  { id: 'links', labelKey: 'outputs.links' }
];

type DetailVm =
  | { status: 'loading'; item: null }
  | { status: 'error'; item: null }
  | { status: 'notfound'; item: null }
  | { status: 'success'; item: ResearchOutputDetailItem };

@Component({
  selector: 'os-research-output-detail',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    RouterLink,
    DetailSectionNavComponent,
    ErrorStateComponent,
    IdentifierRowComponent,
    LoadingStateComponent,
    PageHeaderComponent
  ],
  templateUrl: './research-output-detail.component.html',
  styleUrl: './research-output-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResearchOutputDetailComponent {
  private readonly outputsApi = inject(ResearchOutputApiService);
  private readonly crossrefApi = inject(CrossrefApiService);
  private readonly dspaceApi = inject(DspaceApiService);
  private readonly i18n = inject(I18nService);
  readonly id = input.required<string>();
  readonly language = this.i18n.currentLanguage;

  readonly vm = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) => {
        const numericId = Number(id);
        if (!Number.isFinite(numericId)) {
          return of<DetailVm>({ status: 'notfound', item: null });
        }
        return this.outputsApi.getById(numericId).pipe(
          map((item): DetailVm => ({ status: 'success', item })),
          catchError((error: unknown) =>
            of<DetailVm>(
              error instanceof HttpErrorResponse && error.status === 404
                ? { status: 'notfound', item: null }
                : { status: 'error', item: null }
            )
          ),
          startWith<DetailVm>({ status: 'loading', item: null })
        );
      })
    ),
    { initialValue: { status: 'loading', item: null } satisfies DetailVm }
  );

  readonly crossref = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) => {
        const numericId = Number(id);
        if (!Number.isFinite(numericId)) {
          return of([] as CrossrefWorkSummary[]);
        }
        return this.crossrefApi.byResearchOutput(numericId).pipe(catchError(() => of([] as CrossrefWorkSummary[])));
      })
    ),
    { initialValue: [] as CrossrefWorkSummary[] }
  );

  readonly dspace = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) => {
        const numericId = Number(id);
        if (!Number.isFinite(numericId)) {
          return of([] as DspaceItemSummary[]);
        }
        return this.dspaceApi.byResearchOutput(numericId).pipe(catchError(() => of([] as DspaceItemSummary[])));
      })
    ),
    { initialValue: [] as DspaceItemSummary[] }
  );

  readonly metrics = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) => {
        const numericId = Number(id);
        if (!Number.isFinite(numericId)) {
          return of(null as ResearchOutputMetrics | null);
        }
        return this.outputsApi.getMetrics(numericId).pipe(catchError(() => of(null as ResearchOutputMetrics | null)));
      })
    ),
    { initialValue: null as ResearchOutputMetrics | null }
  );

  readonly metricFetchKey = computed(() => metricFetchMessageKey(metricFetchKind(this.metrics()?.sciVal)));
  readonly metricFetchFailed = computed(() => metricFetchKind(this.metrics()?.sciVal) === 'failed');
  readonly secondaryObservations = computed(() =>
    (this.metrics()?.observations ?? []).filter((obs) => (obs.metricId ?? '').toLowerCase() !== 'fwci')
  );

  readonly headerSubtitle = computed(() => {
    const item = this.vm().item;
    if (!item) {
      return '';
    }
    return [item.year, item.type].filter((value) => value != null && value !== '').join(' · ');
  });

  readonly sectionNav = computed(() => {
    const items = [...RESEARCH_OUTPUT_DETAIL_SECTIONS];
    if (this.crossref().length > 0) {
      items.push({ id: 'crossref', labelKey: 'crossref.title' });
    }
    if (this.dspace().length > 0) {
      items.push({ id: 'dspace', labelKey: 'dspace.title' });
    }
    return items;
  });

  observationValue(obs: { integerValue: number | null; decimalValue: number | null; stringValue: string | null }): string {
    return observationDisplayValue(obs) ?? '—';
  }

  doiHref(doi: string): string {
    return `https://doi.org/${doi}`;
  }

  storedLabel(primary: string | null, fallback: string | null): string {
    return primary || fallback || '';
  }

  workflowLabel(item: {
    workflowStep?: string | null;
    workflowEs?: string | null;
    workflowEn?: string | null;
  }): string {
    return localizedPureTerm(this.language(), item.workflowEs, item.workflowEn, item.workflowStep);
  }

  workflowCode(item: {
    workflowStep?: string | null;
    workflowEs?: string | null;
    workflowEn?: string | null;
  }): string {
    return workflowStepCode(this.workflowLabel(item), item.workflowStep);
  }

  visibilityLabel(item: {
    visibilityKey?: string | null;
    visibilityEs?: string | null;
    visibilityEn?: string | null;
  }): string {
    return localizedPureTerm(this.language(), item.visibilityEs, item.visibilityEn, item.visibilityKey);
  }

  keywordValues(keywords: { value: string | null }[]): string {
    return keywords.map((keyword) => keyword.value).filter((value): value is string => !!value).join(', ');
  }

  dspaceAccessKey(status: string | null | undefined): string {
    if (status === 'open.access') {
      return 'dspace.accessStatus.openAccess';
    }
    if (status === 'metadata.only') {
      return 'dspace.accessStatus.metadataOnly';
    }
    if (status === 'restricted') {
      return 'dspace.accessStatus.restricted';
    }
    if (status === 'embargo') {
      return 'dspace.accessStatus.embargo';
    }
    return 'common.dash';
  }

  dspaceFulltextKey(availability: string | null | undefined): string {
    if (!availability) {
      return 'common.dash';
    }
    return `dspace.fulltextAvailability.${availability}`;
  }
}
