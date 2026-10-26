import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { combineLatest, debounceTime, of, startWith, switchMap } from 'rxjs';
import { I18nService } from '../../core/i18n/i18n.service';
import {
  OrcidAffiliationClass,
  ResearcherIdentityCase,
  ResearcherIdentityIndicatorCode,
  ResearcherIdentitySignatureDetail,
  SortDirection
} from '../../core/models/openscience.models';
import { ResearcherIdentityApiService } from '../../core/services/api/researcher-identity-api.service';
import { NotificationService } from '../../core/services/notification.service';
import { apiErrorCode } from '../../core/utils/api-error';
import { downloadBlob, filenameFromDisposition } from '../../core/utils/download';
import { emptyPage, toPageViewState } from '../../core/utils/page-view-state';
import { DataTableShellComponent } from '../../shared/components/data-table-shell/data-table-shell.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { FilterBarComponent } from '../../shared/components/filter-bar/filter-bar.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';
import { SearchBoxComponent } from '../../shared/components/search-box/search-box.component';
import { SortableHeaderComponent } from '../../shared/components/sortable-header/sortable-header.component';
import { StatusBadgeComponent, OsBadgeTone } from '../../shared/components/status-badge/status-badge.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { nextTableSort, TableSort } from '../../shared/table/table-sort';

const PAGE_SIZE = 25;

@Component({
  selector: 'os-identity-cases',
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
    PageHeaderComponent,
    PaginationComponent,
    SearchBoxComponent,
    SortableHeaderComponent,
    StatusBadgeComponent
  ],
  templateUrl: './identity-cases.component.html',
  styleUrl: './identity-cases.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IdentityCasesComponent {
  private readonly api = inject(ResearcherIdentityApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly i18n = inject(I18nService);
  private readonly notifications = inject(NotificationService);

  readonly indicator = toSignal(
    this.route.paramMap.pipe(switchMap((params) => of(params.get('indicator') ?? ''))),
    { initialValue: this.route.snapshot.paramMap.get('indicator') ?? '' }
  );
  readonly search = new FormControl('', { nonNullable: true });
  readonly classification = new FormControl<OrcidAffiliationClass | 'ALL'>('ALL', { nonNullable: true });
  readonly page = signal(1);
  readonly sortField = signal('name');
  readonly sortDirection = signal<SortDirection>('asc');
  readonly exportBusy = signal(false);
  readonly detail = signal<ResearcherIdentitySignatureDetail | null>(null);
  readonly employmentDetail = signal<ResearcherIdentityCase | null>(null);
  private lastFocus: HTMLElement | null = null;
  private explicitSort = false;
  private readonly defaultSort: TableSort = { field: 'name', direction: 'asc' };

  readonly isOrcid = computed(() => this.indicator() === 'normalizedUssOrcidAffiliation');
  readonly isSignature = computed(
    () =>
      this.indicator() === 'controlledSignature' || this.indicator() === 'highSignatureFragmentation'
  );

  readonly result = toSignal(
    combineLatest({
      indicator: toObservable(this.indicator),
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      classification: this.classification.valueChanges.pipe(startWith(this.classification.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page)
    }).pipe(
      switchMap(({ indicator, search, classification, sortField, sortDirection, page }) =>
        toPageViewState(
          this.api.cases({
            indicator: indicator as ResearcherIdentityIndicatorCode,
            search,
            classification,
            sort: sortField,
            direction: sortDirection,
            page,
            pageSize: PAGE_SIZE
          }),
          PAGE_SIZE
        )
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<ResearcherIdentityCase>(PAGE_SIZE) } }
  );

  constructor() {
    combineLatest([this.search.valueChanges, this.classification.valueChanges])
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.page.set(1));
  }

  titleKey(): string {
    return `indicators.identity.cases.${this.indicator() || 'title'}`;
  }

  onPageChange(page: number): void {
    this.page.set(page);
  }

  onSort(field: string): void {
    const next = nextTableSort(
      { field: this.sortField(), direction: this.sortDirection() },
      field,
      this.defaultSort,
      this.explicitSort
    );
    this.sortField.set(next.sort.field);
    this.sortDirection.set(next.sort.direction);
    this.explicitSort = next.explicit;
    this.page.set(1);
  }

  orcidHref(orcid: string | null): string | null {
    return orcid ? `https://orcid.org/${orcid}` : null;
  }

  classificationKey(value: string | null): string {
    return `indicators.identity.classification.${value ?? 'UNKNOWN'}`;
  }

  classificationTone(value: string | null): OsBadgeTone {
    switch (value) {
      case 'NORMALIZED':
        return 'info';
      case 'USS_VARIANT':
        return 'review';
      case 'USS_NOT_FOUND':
        return 'neutral';
      case 'NOT_EVALUABLE':
        return 'muted';
      default:
        return 'neutral';
    }
  }

  openSignatures(row: ResearcherIdentityCase, event: Event): void {
    this.lastFocus = event.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    this.api.signatureDetail(row.personId).subscribe({
      next: (detail) => this.detail.set(detail),
      error: () =>
        this.notifications.show(
          'error',
          this.i18n.translate('indicators.identity.toast.detailFailedTitle'),
          this.i18n.translate('indicators.identity.toast.detailFailed')
        )
    });
  }

  openEmployments(row: ResearcherIdentityCase, event: Event): void {
    this.lastFocus = event.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    this.employmentDetail.set(row);
  }

  closeDetail(): void {
    this.detail.set(null);
    this.employmentDetail.set(null);
    this.lastFocus?.focus();
    this.lastFocus = null;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.detail() || this.employmentDetail()) {
      this.closeDetail();
    }
  }

  exportExcel(): void {
    if (this.exportBusy()) {
      return;
    }
    this.exportBusy.set(true);
    this.api
      .export({
        indicator: this.indicator(),
        search: this.search.value,
        classification: this.classification.value,
        sort: this.sortField(),
        direction: this.sortDirection()
      })
      .subscribe({
        next: (response) => {
          this.exportBusy.set(false);
          if (response.body) {
            downloadBlob(
              response.body,
              filenameFromDisposition(response.headers.get('Content-Disposition'), 'openscience-identidad.xlsx')
            );
          }
        },
        error: (error: HttpErrorResponse) => {
          this.exportBusy.set(false);
          const code = apiErrorCode(error);
          this.notifications.show(
            'error',
            this.i18n.translate('indicators.identity.toast.exportFailedTitle'),
            this.i18n.translate(
              code === 'EXPORT_EMPTY' ? 'indicators.identity.exportEmpty' : 'indicators.identity.toast.exportFailed'
            )
          );
        }
      });
  }
}
