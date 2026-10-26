import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { AdminRole } from '../../../core/models/openscience.models';
import { AdminRoleApiService } from '../../../core/services/api/admin-role-api.service';
import { AuthService } from '../../../core/services/auth.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { I18N_HOST, I18N_IMPORTS } from '../../../shared/i18n/i18n.imports';
import { RoleFormComponent } from './role-form.component';

type RolesView =
  | { status: 'loading'; items: AdminRole[] }
  | { status: 'error'; items: AdminRole[] }
  | { status: 'success'; items: AdminRole[] };

@Component({
  selector: 'os-roles-list',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    RouterLink,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    RoleFormComponent
  ],
  templateUrl: './roles-list.component.html',
  styleUrl: '../users/users-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RolesListComponent {
  private readonly api = inject(AdminRoleApiService);
  readonly auth = inject(AuthService);
  readonly reloadTick = signal(0);
  readonly editing = signal<AdminRole | 'new' | null>(null);
  readonly roles = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() =>
        this.api.list().pipe(
          map((items): RolesView => ({ status: 'success', items })),
          catchError(() => of<RolesView>({ status: 'error', items: [] })),
          startWith<RolesView>({ status: 'loading', items: [] })
        )
      )
    ),
    { initialValue: { status: 'loading', items: [] } as RolesView }
  );

  reload(): void {
    this.reloadTick.update((value) => value + 1);
  }

  disable(role: AdminRole): void {
    this.api.disable(role.id).subscribe({ next: () => this.reload() });
  }

  duplicate(role: AdminRole): void {
    this.api.duplicate(role.id).subscribe({ next: () => this.reload() });
  }
}
