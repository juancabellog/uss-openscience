import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith, switchMap } from 'rxjs';
import { JournalDetail, JournalIssnItem, JournalMetrics, MetricObservation } from '../../core/models/openscience.models';
import { metricFetchKind, metricFetchMessageKey, observationDisplayValue } from '../../core/utils/pure-metrics.util';
import { I18nService } from '../../core/i18n/i18n.service';
import { JournalApiService } from '../../core/services/api/journal-api.service';
import { DetailSectionNavComponent, DetailSectionNavItem } from '../../shared/components/detail-section-nav/detail-section-nav.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { IdentifierRowComponent } from '../../shared/components/identifier-row/identifier-row.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

export const JOURNAL_DETAIL_SECTIONS: DetailSectionNavItem[] = [
  { id: 'identidad', labelKey: 'journals.identity' },
  { id: 'titulos', labelKey: 'journals.titles' },
  { id: 'issns', labelKey: 'journals.issns' },
  { id: 'identificadores', labelKey: 'journals.identifiers' },
  { id: 'editorial', labelKey: 'journals.publisher' },
  { id: 'asjc', labelKey: 'journals.asjc' },
  { id: 'metricas', labelKey: 'metrics.title' },
  { id: 'auditoria', labelKey: 'journals.pureAudit' }
];

type DetailVm =
  | { status: 'loading'; journal: null; issns: JournalIssnItem[]; metrics: JournalMetrics | null }
  | { status: 'error'; journal: null; issns: JournalIssnItem[]; metrics: JournalMetrics | null }
  | { status: 'notfound'; journal: null; issns: JournalIssnItem[]; metrics: JournalMetrics | null }
  | { status: 'success'; journal: JournalDetail; issns: JournalIssnItem[]; metrics: JournalMetrics | null };

const METRIC_PAGE_SIZE = 50;

@Component({
  selector: 'os-journal-detail',
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
  templateUrl: './journal-detail.component.html',
  styleUrl: './journal-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JournalDetailComponent {
  private readonly journalsApi = inject(JournalApiService);
  private readonly i18n = inject(I18nService);
  readonly id = input.required<string>();
  readonly sectionNav = JOURNAL_DETAIL_SECTIONS;
  readonly quartileLimit = signal(METRIC_PAGE_SIZE);
  readonly citeScoreLimit = signal(METRIC_PAGE_SIZE);

  readonly vm = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) => {
        const numericId = Number(id);
        if (!Number.isFinite(numericId)) {
          return of<DetailVm>({ status: 'notfound', journal: null, issns: [], metrics: null });
        }
        this.quartileLimit.set(METRIC_PAGE_SIZE);
        this.citeScoreLimit.set(METRIC_PAGE_SIZE);
        return forkJoin({
          journal: this.journalsApi.getById(numericId),
          issns: this.journalsApi.getIssns(numericId).pipe(catchError(() => of({ journalId: numericId, count: 0, items: [] }))),
          metrics: this.journalsApi.getMetrics(numericId).pipe(catchError(() => of(null)))
        }).pipe(
          map(({ journal, issns, metrics }): DetailVm => ({ status: 'success', journal, issns: issns.items, metrics })),
          catchError((error: unknown) =>
            of<DetailVm>(
              error instanceof HttpErrorResponse && error.status === 404
                ? { status: 'notfound', journal: null, issns: [], metrics: null }
                : { status: 'error', journal: null, issns: [], metrics: null }
            )
          ),
          startWith<DetailVm>({ status: 'loading', journal: null, issns: [], metrics: null })
        );
      })
    ),
    { initialValue: { status: 'loading', journal: null, issns: [], metrics: null } satisfies DetailVm }
  );

  readonly language = this.i18n.currentLanguage;

  readonly displayTitle = computed(() => {
    const journal = this.vm().journal;
    if (!journal) {
      return '';
    }
    return journal.title || journal.titles[0]?.title || '';
  });

  typeLabel(journal: JournalDetail): string {
    return localized(journal.typeEs, journal.typeEn, journal.type, this.language());
  }

  workflowLabel(journal: JournalDetail): string {
    return localized(journal.workflowEs, journal.workflowEn, journal.workflowStep, this.language());
  }

  groupName(group: JournalDetail['keywordGroups'][number]): string {
    return localized(group.nameEs, group.nameEn, group.logicalName, this.language());
  }

  classificationTerm(item: { termEs?: string | null; termEn?: string | null; uri?: string | null }): string {
    return localized(item.termEs, item.termEn, item.uri, this.language());
  }

  citeScoreRows(): MetricObservation[] {
    return (this.vm().metrics?.citeScoreHistory ?? []).slice(0, this.citeScoreLimit());
  }

  quartileRows() {
    return (this.vm().metrics?.quartileHistory ?? []).slice(0, this.quartileLimit());
  }

  showMoreCiteScore(): void {
    this.citeScoreLimit.update((value) => value + METRIC_PAGE_SIZE);
  }

  showMoreQuartiles(): void {
    this.quartileLimit.update((value) => value + METRIC_PAGE_SIZE);
  }

  fetchKind(collection: 'scopusJournal' | 'scopusCitescore'): ReturnType<typeof metricFetchKind> {
    const metrics = this.vm().metrics;
    return metricFetchKind(collection === 'scopusJournal' ? metrics?.scopusJournal : metrics?.scopusCitescore);
  }

  fetchMessageKey(collection: 'scopusJournal' | 'scopusCitescore'): string {
    return metricFetchMessageKey(this.fetchKind(collection));
  }

  displayValue(obs: MetricObservation): string {
    return observationDisplayValue(obs) ?? '—';
  }

  categoryLabel(obs: { categoryTermEs?: string | null; categoryTermEn?: string | null; categoryUri?: string | null }): string {
    return localized(obs.categoryTermEs, obs.categoryTermEn, obs.categoryUri, this.language());
  }

  identifierType(item: JournalDetail['identifiers'][number]): string {
    return localized(item.typeEs, item.typeEn, item.idSource || item.typeDiscriminator, this.language());
  }
}

function localized(
  es: string | null | undefined,
  en: string | null | undefined,
  fallback: string | null | undefined,
  language: string
): string {
  const preferred = language === 'en' ? en || es : es || en;
  return preferred || fallback || '';
}
