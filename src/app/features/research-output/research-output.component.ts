import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { combineLatest, debounceTime, startWith, switchMap } from 'rxjs';
import { DataQualityStatus, ResearchOutput } from '../../core/models/openscience.models';
import { ResearchOutputService } from '../../core/services/platform-services';
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
  selector: 'os-research-output',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    ReactiveFormsModule,
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
  templateUrl: './research-output.component.html',
  styleUrl: './research-output.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResearchOutputComponent {
  private readonly outputs = inject(ResearchOutputService);
  readonly search = new FormControl('', { nonNullable: true });
  readonly type = new FormControl('all', { nonNullable: true });
  readonly status = new FormControl<'all' | DataQualityStatus>('all', { nonNullable: true });
  readonly page = signal(1);
  readonly qualityKey = qualityKey;
  readonly qualityTone = qualityTone;

  readonly result = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(startWith(''), debounceTime(200)),
      type: this.type.valueChanges.pipe(startWith(this.type.value)),
      status: this.status.valueChanges.pipe(startWith(this.status.value)),
      page: toObservable(this.page)
    }).pipe(
      switchMap(({ search, type, status, page }) =>
        toPageViewState(this.outputs.list({ search, type, status, page, pageSize: 8 }), 8)
      )
    ),
    { initialValue: { status: 'loading', data: emptyPage<ResearchOutput>(8) } }
  );

  constructor() {
    combineLatest([this.search.valueChanges, this.type.valueChanges, this.status.valueChanges])
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.page.set(1));
  }

  onPageChange(page: number): void {
    this.page.set(page);
  }
}
