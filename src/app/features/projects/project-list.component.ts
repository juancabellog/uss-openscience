import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, EMPTY, finalize, map, of, startWith, switchMap, type Observable } from 'rxjs';
import {
  ProjectFilters,
  ProjectItem,
  ProjectPresenceFilter,
  ProjectQuery,
  ProjectSort,
  ProjectStats,
  SortDirection
} from '../../core/models/openscience.models';
import { I18nService } from '../../core/i18n/i18n.service';
import { ProjectApiService } from '../../core/services/api/project-api.service';
import { SyncMonitorService } from '../../core/services/sync-monitor.service';
import { SyncRun } from '../../core/services/api/sync-api.service';
import { emptyPage, toPageViewState } from '../../core/utils/page-view-state';
import { DataTableShellComponent } from '../../shared/components/data-table-shell/data-table-shell.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { FilterBarComponent } from '../../shared/components/filter-bar/filter-bar.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { MiniStatComponent } from '../../shared/components/mini-stat/mini-stat.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';
import { SearchBoxComponent } from '../../shared/components/search-box/search-box.component';
import { SortableHeaderComponent } from '../../shared/components/sortable-header/sortable-header.component';
import { SyncExportBarComponent } from '../../shared/components/sync-export-bar/sync-export-bar.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { ariaSortValue, nextTableSort, TableSort } from '../../shared/table/table-sort';

const PAGE_SIZE = 25;
const EMPTY_FILTERS: ProjectFilters = { types: [], workflows: [], visibilities: [], startYears: [] };
const DEFAULT_SORT: TableSort = { field: 'title', direction: 'asc' };

export function projectHasActiveFilters(state: {
  search?: string;
  typeUri?: string;
  workflowStep?: string;
  visibilityKey?: string;
  presence?: ProjectPresenceFilter;
  startYear?: number | null;
}): boolean {
  return !!(
    state.search?.trim() ||
    state.typeUri ||
    state.workflowStep ||
    state.visibilityKey ||
    (state.presence && state.presence !== 'ACTIVE') ||
    state.startYear
  );
}

export function projectStatsQuery(state: {
  search?: string;
  typeUri?: string;
  workflowStep?: string;
  visibilityKey?: string;
  presence?: ProjectPresenceFilter;
  startYear?: number | null;
  sort?: ProjectSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}): ProjectQuery {
  return {
    search: state.search || undefined,
    typeUri: state.typeUri || undefined,
    workflowStep: state.workflowStep || undefined,
    visibilityKey: state.visibilityKey || undefined,
    presence: state.presence || undefined,
    startYear: state.startYear || undefined
  };
}

@Component({
  selector: 'os-project-list',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    ReactiveFormsModule,
    RouterLink,
    DataTableShellComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    FilterBarComponent,
    LoadingStateComponent,
    MiniStatComponent,
    PageHeaderComponent,
    PaginationComponent,
    SearchBoxComponent,
    SortableHeaderComponent,
    SyncExportBarComponent
  ],
  templateUrl: './project-list.component.html',
  styleUrl: './project-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectListComponent {
  private readonly projectsApi = inject(ProjectApiService);
  private readonly monitor = inject(SyncMonitorService);
  private readonly i18n = inject(I18nService);

  readonly tab = signal<'catalog' | 'history'>('catalog');
  readonly search = new FormControl('', { nonNullable: true });
  readonly typeUri = new FormControl('', { nonNullable: true });
  readonly workflowStep = new FormControl('', { nonNullable: true });
  readonly visibilityKey = new FormControl('', { nonNullable: true });
  readonly presence = new FormControl<ProjectPresenceFilter>('ACTIVE', { nonNullable: true });
  readonly startYear = new FormControl('', { nonNullable: true });
  readonly page = signal(1);
  readonly reloadTick = signal(0);
  readonly sortField = signal<ProjectSort>('title');
  readonly sortDirection = signal<SortDirection>('asc');
  readonly explicitSort = signal(false);
  readonly visualSortField = computed(() => (this.explicitSort() ? this.sortField() : ''));
  readonly statsUpdating = signal(false);
  private statsRequestId = 0;

  readonly filters = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.projectsApi.filters().pipe(catchError(() => of(EMPTY_FILTERS))))
    ),
    { initialValue: EMPTY_FILTERS }
  );

  readonly viewStats = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      typeUri: this.typeUri.valueChanges.pipe(startWith(this.typeUri.value)),
      workflowStep: this.workflowStep.valueChanges.pipe(startWith(this.workflowStep.value)),
      visibilityKey: this.visibilityKey.valueChanges.pipe(startWith(this.visibilityKey.value)),
      presence: this.presence.valueChanges.pipe(startWith(this.presence.value)),
      startYear: this.startYear.valueChanges.pipe(startWith(this.startYear.value)),
      reload: toObservable(this.reloadTick)
    }).pipe(
      debounceTime(0),
      switchMap((state): Observable<ProjectStats | null> => {
        const requestId = ++this.statsRequestId;
        queueMicrotask(() => {
          if (requestId === this.statsRequestId) {
            this.statsUpdating.set(true);
          }
        });
        return this.projectsApi.stats(projectStatsQuery({
          ...state,
          startYear: state.startYear ? Number(state.startYear) : null
        })).pipe(
          map((stats): ProjectStats | null => stats),
          catchError((): Observable<ProjectStats | null> => EMPTY),
          finalize(() => {
            if (requestId === this.statsRequestId) {
              this.statsUpdating.set(false);
            }
          })
        );
      })
    ),
    { initialValue: null as ProjectStats | null }
  );

  readonly stats = computed(() => this.viewStats());
  readonly hasActiveFilters = computed(() => {
    this.stats();
    this.statsUpdating();
    return projectHasActiveFilters({
      search: this.search.value,
      typeUri: this.typeUri.value,
      workflowStep: this.workflowStep.value,
      visibilityKey: this.visibilityKey.value,
      presence: this.presence.value,
      startYear: this.startYear.value ? Number(this.startYear.value) : null
    });
  });

  readonly result = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      typeUri: this.typeUri.valueChanges.pipe(startWith(this.typeUri.value)),
      workflowStep: this.workflowStep.valueChanges.pipe(startWith(this.workflowStep.value)),
      visibilityKey: this.visibilityKey.valueChanges.pipe(startWith(this.visibilityKey.value)),
      presence: this.presence.valueChanges.pipe(startWith(this.presence.value)),
      startYear: this.startYear.valueChanges.pipe(startWith(this.startYear.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      reload: toObservable(this.reloadTick)
    }).pipe(
      switchMap(({ search, typeUri, workflowStep, visibilityKey, presence, startYear, sortField, sortDirection, page }) =>
        toPageViewState(
          this.projectsApi.list({
            search,
            typeUri: typeUri || undefined,
            workflowStep: workflowStep || undefined,
            visibilityKey: visibilityKey || undefined,
            presence: presence || undefined,
            startYear: startYear ? Number(startYear) : undefined,
            sort: sortField,
            direction: sortDirection,
            page,
            pageSize: PAGE_SIZE
          }),
          PAGE_SIZE
        )
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<ProjectItem>(PAGE_SIZE) } }
  );

  readonly historyState = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() =>
        this.projectsApi.history().pipe(
          map((items) => ({ status: 'success' as const, items })),
          catchError(() => of({ status: 'error' as const, items: [] as SyncRun[] })),
          startWith({ status: 'loading' as const, items: [] as SyncRun[] })
        )
      )
    ),
    { initialValue: { status: 'loading' as const, items: [] as SyncRun[] } }
  );

  readonly language = this.i18n.currentLanguage;

  constructor() {
    combineLatest([
      this.search.valueChanges,
      this.typeUri.valueChanges,
      this.workflowStep.valueChanges,
      this.visibilityKey.valueChanges,
      this.presence.valueChanges,
      this.startYear.valueChanges
    ])
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.page.set(1));
  }

  currentQuery(): ProjectQuery {
    return {
      search: this.search.value,
      typeUri: this.typeUri.value || undefined,
      workflowStep: this.workflowStep.value || undefined,
      visibilityKey: this.visibilityKey.value || undefined,
      presence: this.presence.value || undefined,
      startYear: this.startYear.value ? Number(this.startYear.value) : undefined,
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
      DEFAULT_SORT,
      this.explicitSort()
    );
    this.sortField.set(next.sort.field as ProjectSort);
    this.sortDirection.set(next.sort.direction);
    this.explicitSort.set(next.explicit);
    this.page.set(1);
  }

  ariaSort(field: string): 'ascending' | 'descending' | 'none' {
    if (!this.explicitSort()) {
      return 'none';
    }
    return ariaSortValue(field, this.sortField(), this.sortDirection());
  }

  reload(): void {
    this.reloadTick.update((value) => value + 1);
  }

  clearFilters(): void {
    this.search.setValue('');
    this.typeUri.setValue('');
    this.workflowStep.setValue('');
    this.visibilityKey.setValue('');
    this.presence.setValue('ACTIVE');
    this.startYear.setValue('');
    this.page.set(1);
  }

  openHistoryDetail(syncId: string): void {
    this.monitor.openDetail(syncId);
  }

  managingOrganizationLabel(item: ProjectItem): string {
    return localized(
      item.managingOrganizationNameEs,
      item.managingOrganizationNameEn,
      item.managingOrganizationName || item.managingOrganizationUuid,
      this.language()
    );
  }

  displayTitle(item: ProjectItem): string {
    return item.title || item.titleEs || item.titleEn || item.acronym || this.i18n.translate('projects.untitled');
  }

  typeLabel(item: { type?: string | null; typeEs?: string | null; typeEn?: string | null }): string {
    return localized(item.typeEs, item.typeEn, item.type, this.language());
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

  visibilityLabel(item: {
    visibilityKey?: string | null;
    visibilityEs?: string | null;
    visibilityEn?: string | null;
    key?: string | null;
    labelEs?: string | null;
    labelEn?: string | null;
  }): string {
    return localized(
      item.visibilityEs ?? item.labelEs,
      item.visibilityEn ?? item.labelEn,
      item.visibilityKey ?? item.key,
      this.language()
    );
  }

  statusKey(status?: string | null): string {
    return status ? `sync.status.${status}` : 'common.dash';
  }

  modeKey(mode?: string | null): string {
    if (mode === 'INCREMENTAL') {
      return 'sync.mode.incremental';
    }
    return 'sync.mode.full';
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
