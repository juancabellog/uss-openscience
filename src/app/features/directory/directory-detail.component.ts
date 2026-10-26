import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';
import { DirectoryPersonDetail } from '../../core/models/openscience.models';
import { DirectoryApiService } from '../../core/services/api/directory-api.service';
import { DetailSectionNavComponent, DetailSectionNavItem } from '../../shared/components/detail-section-nav/detail-section-nav.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { IdentifierRowComponent } from '../../shared/components/identifier-row/identifier-row.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

export const DIRECTORY_DETAIL_SECTIONS: DetailSectionNavItem[] = [
  { id: 'identidad', labelKey: 'directory.identity' },
  { id: 'institucional', labelKey: 'directory.institutional' },
  { id: 'correos', labelKey: 'directory.emails' }
];

type DetailVm =
  | { status: 'loading'; person: null }
  | { status: 'error'; person: null }
  | { status: 'notfound'; person: null }
  | { status: 'success'; person: DirectoryPersonDetail };

@Component({
  selector: 'os-directory-detail',
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
  templateUrl: './directory-detail.component.html',
  styleUrl: './directory-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DirectoryDetailComponent {
  private readonly api = inject(DirectoryApiService);
  readonly id = input.required<string>();
  readonly sectionNav = DIRECTORY_DETAIL_SECTIONS;

  readonly vm = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) => {
        const numericId = Number(id);
        if (!Number.isFinite(numericId)) {
          return of<DetailVm>({ status: 'notfound', person: null });
        }
        return this.api.getById(numericId).pipe(
          map((person): DetailVm => ({ status: 'success', person })),
          catchError((error: unknown) =>
            of<DetailVm>(
              error instanceof HttpErrorResponse && error.status === 404
                ? { status: 'notfound', person: null }
                : { status: 'error', person: null }
            )
          )
        );
      })
    ),
    { initialValue: { status: 'loading', person: null } as DetailVm }
  );

  readonly displayName = computed(() => this.vm().person?.displayName ?? '');
}
