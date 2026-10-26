import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';
import {
  OrganizationDetail,
  OrganizationPerson,
  OrganizationProject,
  PagedResult
} from '../../core/models/openscience.models';
import { I18nService } from '../../core/i18n/i18n.service';
import { OrganizationApiService } from '../../core/services/api/organization-api.service';
import { DetailSectionNavComponent, DetailSectionNavItem } from '../../shared/components/detail-section-nav/detail-section-nav.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { IdentifierRowComponent } from '../../shared/components/identifier-row/identifier-row.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

export const ORGANIZATION_DETAIL_SECTIONS: DetailSectionNavItem[] = [
  { id: 'identidad', labelKey: 'organizations.identity' },
  { id: 'jerarquia', labelKey: 'organizations.hierarchy' },
  { id: 'personas', labelKey: 'organizations.relatedPersons' },
  { id: 'proyectos', labelKey: 'organizations.relatedProjects' },
  { id: 'auditoria', labelKey: 'organizations.pureAudit' }
];

const PAGE_SIZE = 25;

type DetailVm =
  | { status: 'loading'; organization: null }
  | { status: 'error'; organization: null }
  | { status: 'notfound'; organization: null }
  | { status: 'success'; organization: OrganizationDetail };

@Component({
  selector: 'os-organization-detail',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    RouterLink,
    DetailSectionNavComponent,
    ErrorStateComponent,
    IdentifierRowComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    PaginationComponent
  ],
  templateUrl: './organization-detail.component.html',
  styleUrl: './organization-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OrganizationDetailComponent {
  private readonly organizationsApi = inject(OrganizationApiService);
  private readonly i18n = inject(I18nService);
  readonly id = input.required<string>();
  readonly sectionNav = ORGANIZATION_DETAIL_SECTIONS;
  readonly personsPage = signal(1);
  readonly projectsPage = signal(1);

  readonly vm = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) => {
        const numericId = Number(id);
        if (!Number.isFinite(numericId)) {
          return of<DetailVm>({ status: 'notfound', organization: null });
        }
        this.personsPage.set(1);
        this.projectsPage.set(1);
        return this.organizationsApi.getById(numericId).pipe(
          map((organization): DetailVm => ({ status: 'success', organization })),
          catchError((error: unknown) =>
            of<DetailVm>(
              error instanceof HttpErrorResponse && error.status === 404
                ? { status: 'notfound', organization: null }
                : { status: 'error', organization: null }
            )
          ),
          startWith<DetailVm>({ status: 'loading', organization: null })
        );
      })
    ),
    { initialValue: { status: 'loading', organization: null } satisfies DetailVm }
  );

  readonly language = this.i18n.currentLanguage;

  readonly persons = toSignal(
    combineLatest({
      id: toObservable(this.id),
      page: toObservable(this.personsPage)
    }).pipe(
      switchMap(({ id, page }) => {
        const numericId = Number(id);
        if (!Number.isFinite(numericId)) {
          return of(emptyRelated<OrganizationPerson>());
        }
        return this.organizationsApi.relatedPersons(numericId, page, PAGE_SIZE).pipe(
          catchError(() => of(emptyRelated<OrganizationPerson>()))
        );
      })
    ),
    { initialValue: emptyRelated<OrganizationPerson>() }
  );

  readonly projects = toSignal(
    combineLatest({
      id: toObservable(this.id),
      page: toObservable(this.projectsPage)
    }).pipe(
      switchMap(({ id, page }) => {
        const numericId = Number(id);
        if (!Number.isFinite(numericId)) {
          return of(emptyRelated<OrganizationProject>());
        }
        return this.organizationsApi.relatedProjects(numericId, page, PAGE_SIZE).pipe(
          catchError(() => of(emptyRelated<OrganizationProject>()))
        );
      })
    ),
    { initialValue: emptyRelated<OrganizationProject>() }
  );

  readonly displayName = computed(() => {
    const organization = this.vm().organization;
    if (!organization) {
      return '';
    }
    return localized(organization.nameEs, organization.nameEn, organization.name || organization.uuid, this.language());
  });

  typeLabel(organization: OrganizationDetail): string {
    return localized(organization.typeEs, organization.typeEn, organization.type, this.language());
  }

  parentLabel(organization: OrganizationDetail): string {
    return localized(
      organization.parentNameEs,
      organization.parentNameEn,
      organization.parentName || organization.parentUuid,
      this.language()
    );
  }

  projectTitle(item: OrganizationProject): string {
    return localized(item.titleEs, item.titleEn, item.title, this.language()) || this.i18n.translate('common.dash');
  }

  relationLabel(item: OrganizationProject): string {
    if (item.relationType === 'MANAGING') {
      return this.i18n.translate('organizations.relationManaging');
    }
    if (item.relationType === 'ASSOCIATED') {
      return this.i18n.translate('organizations.relationAssociated');
    }
    if (item.relationType === 'PARTICIPANT') {
      return this.i18n.translate('organizations.relationParticipant');
    }
    return item.relationType || this.i18n.translate('common.dash');
  }
}

function emptyRelated<T>(): PagedResult<T> {
  return { items: [], total: 0, page: 1, pageSize: PAGE_SIZE };
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
