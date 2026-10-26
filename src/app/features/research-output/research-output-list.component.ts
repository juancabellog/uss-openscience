import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, EMPTY, finalize, map, of, startWith, switchMap, type Observable } from 'rxjs';
import {
  ResearchOutputDoiStatus,
  ResearchOutputFilters,
  ResearchOutputItem,
  ResearchOutputQuery,
  ResearchOutputSort,
  ResearchOutputStats,
  SortDirection
} from '../../core/models/openscience.models';
import { I18nService } from '../../core/i18n/i18n.service';
import { ResearchOutputApiService } from '../../core/services/api/research-output-api.service';
import { SyncApiService, SyncEntity, SyncRun, syncEntityLabelKey } from '../../core/services/api/sync-api.service';
import { SyncMonitorService } from '../../core/services/sync-monitor.service';
import { syncModeLabelKey } from '../../core/sync/sync-mode.util';
import { emptyPage, toPageViewState } from '../../core/utils/page-view-state';
import { DataTableShellComponent } from '../../shared/components/data-table-shell/data-table-shell.component';
import { DoiVersionsCellComponent } from '../../shared/components/doi-versions-cell/doi-versions-cell.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { FilterBarComponent } from '../../shared/components/filter-bar/filter-bar.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';
import { MiniStatComponent } from '../../shared/components/mini-stat/mini-stat.component';
import { SearchBoxComponent } from '../../shared/components/search-box/search-box.component';
import { SortableHeaderComponent } from '../../shared/components/sortable-header/sortable-header.component';
import { SyncExportBarComponent } from '../../shared/components/sync-export-bar/sync-export-bar.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { ariaSortValue, nextTableSort, TableSort } from '../../shared/table/table-sort';

const PAGE_SIZE = 25;
export const OUTPUT_HISTORY_ENTITIES: SyncEntity[] = ['RESEARCH_OUTPUT', 'RESEARCH_OUTPUT_METRICS'];
const EMPTY_FILTERS: ResearchOutputFilters = { types: [], years: [], workflows: [] };
const DEFAULT_SORT: TableSort = { field: 'year', direction: 'desc' };
const MULTIPLE_DOI_SORT: TableSort = { field: 'doiVersionCount', direction: 'desc' };
const SHARED_DOI_SORT: TableSort = { field: 'doi', direction: 'asc' };

export function outputHasActiveFilters(state: {
  search?: string;
  type?: string;
  year?: string | number | null;
  doiStatus?: ResearchOutputDoiStatus;
  workflowStep?: string;
}): boolean {
  return !!(
    state.search?.trim() ||
    state.type ||
    (state.year !== '' && state.year != null) ||
    (state.doiStatus && state.doiStatus !== 'ALL') ||
    state.workflowStep
  );
}

export function outputStatsQuery(state: {
  search?: string;
  type?: string;
  year?: number;
  doiStatus?: ResearchOutputDoiStatus;
  workflowStep?: string;
  sort?: ResearchOutputSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}): ResearchOutputQuery {
  return {
    search: state.search || undefined,
    type: state.type || undefined,
    year: state.year,
    doiStatus: state.doiStatus,
    workflowStep: state.workflowStep || undefined
  };
}

@Component({
  selector: 'os-research-output-list',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    ReactiveFormsModule,
    RouterLink,
    DataTableShellComponent,
    DoiVersionsCellComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    FilterBarComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    PaginationComponent,
    MiniStatComponent,
    SearchBoxComponent,
    SortableHeaderComponent,
    SyncExportBarComponent
  ],
  templateUrl: './research-output-list.component.html',
  styleUrl: './research-output-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResearchOutputListComponent {
  private readonly outputsApi = inject(ResearchOutputApiService);
  private readonly syncApi = inject(SyncApiService);
  private readonly monitor = inject(SyncMonitorService);
  private readonly i18n = inject(I18nService);
  readonly tab = signal<'catalog' | 'history'>('catalog');

  readonly search = new FormControl('', { nonNullable: true });
  readonly type = new FormControl('', { nonNullable: true });
  readonly year = new FormControl('', { nonNullable: true });
  readonly doiStatus = new FormControl<ResearchOutputDoiStatus>('ALL', { nonNullable: true });
  readonly workflowStep = new FormControl('', { nonNullable: true });
  readonly language = this.i18n.currentLanguage;
  readonly page = signal(1);
  readonly reloadTick = signal(0);
  readonly sortField = signal<ResearchOutputSort>('year');
  readonly sortDirection = signal<SortDirection>('desc');
  readonly explicitSort = signal(false);
  readonly statsUpdating = signal(false);
  private statsRequestId = 0;

  readonly viewStats = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      type: this.type.valueChanges.pipe(startWith(this.type.value)),
      year: this.year.valueChanges.pipe(startWith(this.year.value)),
      doiStatus: this.doiStatus.valueChanges.pipe(startWith(this.doiStatus.value)),
      workflowStep: this.workflowStep.valueChanges.pipe(startWith(this.workflowStep.value)),
      reload: toObservable(this.reloadTick)
    }).pipe(
      debounceTime(0),
      switchMap((state): Observable<ResearchOutputStats | null> => {
        const requestId = ++this.statsRequestId;
        queueMicrotask(() => {
          if (requestId === this.statsRequestId) {
            this.statsUpdating.set(true);
          }
        });
        return this.outputsApi
          .stats(
            outputStatsQuery({
              ...state,
              type: state.type || undefined,
              year: state.year ? Number(state.year) : undefined,
              workflowStep: state.workflowStep || undefined
            })
          )
          .pipe(
            map((stats): ResearchOutputStats | null => stats),
            catchError((): Observable<ResearchOutputStats | null> => EMPTY),
            finalize(() => {
              if (requestId === this.statsRequestId) {
                this.statsUpdating.set(false);
              }
            })
          );
      })
    ),
    { initialValue: null as ResearchOutputStats | null }
  );

  readonly stats = computed(() => this.viewStats());
  readonly hasActiveFilters = computed(() => {
    this.stats();
    this.statsUpdating();
    return outputHasActiveFilters({
      search: this.search.value,
      type: this.type.value,
      year: this.year.value,
      doiStatus: this.doiStatus.value,
      workflowStep: this.workflowStep.value
    });
  });

  readonly filters = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.outputsApi.filters().pipe(catchError(() => of(EMPTY_FILTERS))))
    ),
    { initialValue: EMPTY_FILTERS }
  );

  readonly result = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      type: this.type.valueChanges.pipe(startWith(this.type.value)),
      year: this.year.valueChanges.pipe(startWith(this.year.value)),
      doiStatus: this.doiStatus.valueChanges.pipe(startWith(this.doiStatus.value)),
      workflowStep: this.workflowStep.valueChanges.pipe(startWith(this.workflowStep.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      reload: toObservable(this.reloadTick)
    }).pipe(
      debounceTime(0),
      switchMap(({ search, type, year, doiStatus, workflowStep, sortField, sortDirection, page }) =>
        toPageViewState(
          this.outputsApi.list({
            search,
            type: type || undefined,
            year: year ? Number(year) : undefined,
            doiStatus,
            workflowStep: workflowStep || undefined,
            sort: sortField,
            direction: sortDirection,
            page,
            pageSize: PAGE_SIZE
          }),
          PAGE_SIZE
        )
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<ResearchOutputItem>(PAGE_SIZE) } }
  );

  readonly historyState = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() =>
        this.syncApi.history(OUTPUT_HISTORY_ENTITIES).pipe(
          map((items) => ({ status: 'success' as const, items })),
          catchError(() => of({ status: 'error' as const, items: [] as SyncRun[] })),
          startWith({ status: 'loading' as const, items: [] as SyncRun[] })
        )
      )
    ),
    { initialValue: { status: 'loading' as const, items: [] as SyncRun[] } }
  );

  constructor() {
    combineLatest([
      this.search.valueChanges,
      this.type.valueChanges,
      this.year.valueChanges,
      this.doiStatus.valueChanges,
      this.workflowStep.valueChanges
    ])
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        this.page.set(1);
        if (!this.explicitSort()) {
          this.applyContextualSort(this.doiStatus.value);
        }
      });
  }

  currentQuery(): ResearchOutputQuery {
    return {
      search: this.search.value,
      type: this.type.value || undefined,
      year: this.year.value ? Number(this.year.value) : undefined,
      doiStatus: this.doiStatus.value,
      workflowStep: this.workflowStep.value || undefined,
      sort: this.sortField(),
      direction: this.sortDirection()
    };
  }

  onPageChange(page: number): void {
    this.page.set(page);
  }

  onSort(field: string): void {
    const next = nextTableSort(
      { field: this.sortField(), direction: this.sortDirection() },
      field,
      this.defaultSort(this.doiStatus.value),
      this.explicitSort()
    );
    this.sortField.set(next.sort.field as ResearchOutputSort);
    this.sortDirection.set(next.sort.direction);
    this.explicitSort.set(next.explicit);
    this.page.set(1);
  }

  ariaSort(field: string): 'ascending' | 'descending' | 'none' {
    return ariaSortValue(field, this.sortField(), this.sortDirection());
  }

  reload(): void {
    this.reloadTick.update((value) => value + 1);
  }

  openHistoryDetail(syncId: string): void {
    this.monitor.openDetail(syncId);
  }

  operationKey(entity?: string | null): string {
    return syncEntityLabelKey(entity);
  }

  modeKey(mode?: string | null): string {
    return syncModeLabelKey(mode) ?? 'common.dash';
  }

  statusKey(status?: string | null): string {
    return status ? `sync.status.${status}` : 'common.dash';
  }

  clearFilters(): void {
    this.search.setValue('');
    this.type.setValue('');
    this.year.setValue('');
    this.doiStatus.setValue('ALL');
    this.workflowStep.setValue('');
    this.explicitSort.set(false);
    this.page.set(1);
  }

  doiHref(doi: string): string {
    return `https://doi.org/${doi}`;
  }

  typeLabel(typeEn: string | null, typeEs: string | null): string {
    return typeEn || typeEs || '';
  }

  workflowLabel(item: {
    workflowStep?: string | null;
    workflowEs?: string | null;
    workflowEn?: string | null;
    step?: string | null;
    labelEs?: string | null;
    labelEn?: string | null;
  }): string {
    return localized(
      item.workflowEs ?? item.labelEs,
      item.workflowEn ?? item.labelEn,
      item.workflowStep ?? item.step,
      this.language()
    );
  }

  private applyContextualSort(status: ResearchOutputDoiStatus): void {
    if (status === 'SHARED_DOI' || status === 'MULTIPLE_DOI_VERSIONS') {
      this.applyDefaultSort(status);
      return;
    }
    if (this.sortField() === SHARED_DOI_SORT.field && this.sortDirection() === SHARED_DOI_SORT.direction) {
      return;
    }
    this.applyDefaultSort(status);
  }

  private applyDefaultSort(status: ResearchOutputDoiStatus): void {
    const fallback = this.defaultSort(status);
    this.sortField.set(fallback.field as ResearchOutputSort);
    this.sortDirection.set(fallback.direction);
  }

  private defaultSort(status: ResearchOutputDoiStatus): TableSort {
    if (status === 'SHARED_DOI') {
      return SHARED_DOI_SORT;
    }
    return status === 'MULTIPLE_DOI_VERSIONS' ? MULTIPLE_DOI_SORT : DEFAULT_SORT;
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
