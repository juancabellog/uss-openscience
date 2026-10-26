import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { combineLatest, debounceTime, startWith, switchMap } from 'rxjs';
import { DataQualityStatus, Researcher } from '../../core/models/openscience.models';
import { ResearcherService } from '../../core/services/platform-services';
import { emptyPage, toPageViewState } from '../../core/utils/page-view-state';
import { DataTableShellComponent } from '../../shared/components/data-table-shell/data-table-shell.component';
import { DemoBannerComponent } from '../../shared/components/demo-banner/demo-banner.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { FilterBarComponent } from '../../shared/components/filter-bar/filter-bar.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';
import { SearchBoxComponent } from '../../shared/components/search-box/search-box.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { qualityKey, qualityTone } from '../../shared/utils/status.util';

@Component({
  selector: 'os-researcher-list',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    ReactiveFormsModule,
    RouterLink,
    DataTableShellComponent,
    DemoBannerComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    FilterBarComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    PaginationComponent,
    SearchBoxComponent,
    StatusBadgeComponent
  ],
  templateUrl: './researcher-list.component.html',
  styleUrl: './researcher-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResearcherListComponent {
  private readonly researchers = inject(ResearcherService);

  readonly search = new FormControl('', { nonNullable: true });
  readonly faculty = new FormControl('all', { nonNullable: true });
  readonly status = new FormControl<'all' | DataQualityStatus>('all', { nonNullable: true });
  readonly page = signal(1);
  readonly faculties = [
    'Medicina y Ciencia',
    'Ingeniería, Arquitectura y Diseño',
    'Psicología',
    'Odontología y Ciencias de la Rehabilitación',
    'Educación',
    'Derecho y Gobierno',
    'Enfermería',
    'Economía y Negocios'
  ];
  readonly qualityKey = qualityKey;
  readonly qualityTone = qualityTone;

  readonly result = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(startWith(this.search.value), debounceTime(200)),
      faculty: this.faculty.valueChanges.pipe(startWith(this.faculty.value)),
      status: this.status.valueChanges.pipe(startWith(this.status.value)),
      page: toObservable(this.page)
    }).pipe(
      switchMap(({ search, faculty, status, page }) =>
        toPageViewState(
          this.researchers.list({
            search,
            faculty,
            status,
            page,
            pageSize: 6
          }),
          6
        )
      )
    ),
    { initialValue: { status: 'loading', data: emptyPage<Researcher>(6) } }
  );

  constructor() {
    combineLatest([this.search.valueChanges, this.faculty.valueChanges, this.status.valueChanges])
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.page.set(1));
  }

  onPageChange(page: number): void {
    this.page.set(page);
  }
}
