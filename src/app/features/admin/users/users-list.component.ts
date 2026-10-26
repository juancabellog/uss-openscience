import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, of, startWith, switchMap } from 'rxjs';
import { AdminRole, AdminUserQuery, AdminUserSummary } from '../../../core/models/openscience.models';
import { AdminRoleApiService } from '../../../core/services/api/admin-role-api.service';
import { AdminUserApiService } from '../../../core/services/api/admin-user-api.service';
import { AuthService } from '../../../core/services/auth.service';
import { emptyPage, toPageViewState } from '../../../core/utils/page-view-state';
import { DataTableShellComponent } from '../../../shared/components/data-table-shell/data-table-shell.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { FilterBarComponent } from '../../../shared/components/filter-bar/filter-bar.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';
import { SearchBoxComponent } from '../../../shared/components/search-box/search-box.component';
import { SortableHeaderComponent } from '../../../shared/components/sortable-header/sortable-header.component';
import { I18N_HOST, I18N_IMPORTS } from '../../../shared/i18n/i18n.imports';
import { nextTableSort, TableSort } from '../../../shared/table/table-sort';
import { UserFormComponent } from './user-form.component';
import { canOfferUserDelete, canOfferUserMutations } from './user-admin-actions';

const PAGE_SIZE = 25;

@Component({
  selector: 'os-users-list',
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
    UserFormComponent
  ],
  templateUrl: './users-list.component.html',
  styleUrl: './users-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UsersListComponent {
  private readonly api = inject(AdminUserApiService);
  private readonly rolesApi = inject(AdminRoleApiService);
  readonly auth = inject(AuthService);
  readonly search = new FormControl('', { nonNullable: true });
  readonly status = new FormControl('', { nonNullable: true });
  readonly roleId = new FormControl('', { nonNullable: true });
  readonly page = signal(1);
  readonly reloadTick = signal(0);
  readonly sortField = signal<NonNullable<AdminUserQuery['sort']>>('createdAt');
  readonly sortDirection = signal<'asc' | 'desc'>('desc');
  readonly explicitSort = signal(false);
  readonly editing = signal<AdminUserSummary | 'new' | null>(null);
  readonly deleting = signal<AdminUserSummary | null>(null);
  readonly deleteBusy = signal(false);
  readonly message = signal<string | null>(null);
  readonly roles = toSignal(this.rolesApi.list().pipe(catchError(() => of<AdminRole[]>([]))), {
    initialValue: [] as AdminRole[]
  });
  readonly result = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      status: this.status.valueChanges.pipe(startWith(this.status.value)),
      roleId: this.roleId.valueChanges.pipe(startWith(this.roleId.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      reload: toObservable(this.reloadTick)
    }).pipe(
      debounceTime(0),
      switchMap((state) =>
        toPageViewState(
          this.api.list({
            search: state.search || undefined,
            status: state.status || undefined,
            roleId: state.roleId ? Number(state.roleId) : undefined,
            sort: state.sortField,
            direction: state.sortDirection,
            page: state.page,
            pageSize: PAGE_SIZE
          }),
          PAGE_SIZE
        )
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<AdminUserSummary>(PAGE_SIZE) } }
  );

  onSort(field: string): void {
    const fallback: TableSort = { field: 'createdAt', direction: 'desc' };
    const next = nextTableSort(
      { field: this.sortField(), direction: this.sortDirection() },
      field,
      fallback,
      this.explicitSort()
    );
    this.sortField.set(next.sort.field as NonNullable<AdminUserQuery['sort']>);
    this.sortDirection.set(next.sort.direction);
    this.explicitSort.set(next.explicit);
    this.page.set(1);
  }

  goToPage(page: number): void {
    this.page.set(page);
  }

  reload(): void {
    this.reloadTick.update((value) => value + 1);
  }

  disable(row: AdminUserSummary): void {
    this.api.disable(row.id).subscribe({ next: () => this.reload() });
  }

  enable(row: AdminUserSummary): void {
    this.api.enable(row.id).subscribe({ next: () => this.reload() });
  }

  resend(row: AdminUserSummary): void {
    this.api.resend(row.id).subscribe({
      next: (result) => {
        this.message.set(result.emailSent ? 'admin.users.resent' : 'admin.users.createdNoMail');
        this.reload();
      }
    });
  }

  canMutate(row: AdminUserSummary): boolean {
    return this.auth.canAdminister('ADMINISTRATION') && canOfferUserMutations(row);
  }

  canDelete(row: AdminUserSummary): boolean {
    return canOfferUserDelete(row, {
      currentUserId: this.auth.user()?.id,
      canAdminister: this.auth.canAdminister('ADMINISTRATION')
    });
  }

  askDelete(row: AdminUserSummary): void {
    if (!this.canDelete(row) || this.deleteBusy()) {
      return;
    }
    this.deleting.set(row);
  }

  cancelDelete(): void {
    if (this.deleteBusy()) {
      return;
    }
    this.deleting.set(null);
  }

  confirmDelete(): void {
    const row = this.deleting();
    if (!row || this.deleteBusy()) {
      return;
    }
    this.deleteBusy.set(true);
    this.api.delete(row.id).subscribe({
      next: () => {
        this.message.set('admin.users.deleted');
        this.deleting.set(null);
        this.deleteBusy.set(false);
        this.reload();
      },
      error: () => {
        this.message.set('admin.users.deleteError');
        this.deleteBusy.set(false);
      }
    });
  }
}
