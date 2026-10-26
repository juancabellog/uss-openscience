import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { OrcidWork, PersonAffiliation, PersonDetail, PersonMetrics, PersonOrcidWorks } from '../../core/models/openscience.models';
import { I18nService } from '../../core/i18n/i18n.service';
import { metricFetchKind, metricFetchMessageKey, personMetricMatrix } from '../../core/utils/pure-metrics.util';
import { orcidFirstSeenLabelKey } from '../../core/utils/orcid-adoption.util';
import { PersonApiService } from '../../core/services/api/person-api.service';
import { AuthService } from '../../core/services/auth.service';
import { DetailSectionNavComponent, DetailSectionNavItem } from '../../shared/components/detail-section-nav/detail-section-nav.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { IdentifierRowComponent } from '../../shared/components/identifier-row/identifier-row.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

export const PEOPLE_DETAIL_SECTIONS: DetailSectionNavItem[] = [
  { id: 'resumen', labelKey: 'people.summary' },
  { id: 'identidad-institucional', labelKey: 'people.institutionalIdentity' },
  { id: 'organizaciones', labelKey: 'people.affiliations' },
  { id: 'identidad', labelKey: 'people.identity' },
  { id: 'metricas', labelKey: 'metrics.title' },
  { id: 'orcid-works', labelKey: 'people.orcidWorks' }
];

type DetailVm =
  | { status: 'loading'; person: null }
  | { status: 'error'; person: null }
  | { status: 'notfound'; person: null }
  | { status: 'success'; person: PersonDetail };

@Component({
  selector: 'os-people-detail',
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
  templateUrl: './people-detail.component.html',
  styleUrl: './people-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PeopleDetailComponent {
  private readonly peopleApi = inject(PersonApiService);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  readonly id = input.required<string>();
  readonly sectionNav = PEOPLE_DETAIL_SECTIONS;
  readonly orcidReload = signal(0);
  readonly orcidBusy = signal(false);
  readonly orcidMessage = signal<string | null>(null);

  readonly vm = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) => {
        const numericId = Number(id);
        if (!Number.isFinite(numericId)) {
          return of<DetailVm>({ status: 'notfound', person: null });
        }
        return this.peopleApi.getById(numericId).pipe(
          map((person): DetailVm => ({ status: 'success', person })),
          catchError((error: unknown) =>
            of<DetailVm>(
              error instanceof HttpErrorResponse && error.status === 404
                ? { status: 'notfound', person: null }
                : { status: 'error', person: null }
            )
          ),
          startWith<DetailVm>({ status: 'loading', person: null })
        );
      })
    ),
    { initialValue: { status: 'loading', person: null } satisfies DetailVm }
  );

  readonly orcidWorks = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) =>
        toObservable(this.orcidReload).pipe(
          switchMap(() => {
            const numericId = Number(id);
            if (!Number.isFinite(numericId)) {
              return of<PersonOrcidWorks | null>(null);
            }
            return this.peopleApi.getOrcidWorks(numericId).pipe(catchError(() => of(null)));
          })
        )
      )
    ),
    { initialValue: null as PersonOrcidWorks | null }
  );

  readonly metrics = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) => {
        const numericId = Number(id);
        if (!Number.isFinite(numericId)) {
          return of(null as PersonMetrics | null);
        }
        return this.peopleApi.getMetrics(numericId).pipe(catchError(() => of(null as PersonMetrics | null)));
      })
    ),
    { initialValue: null as PersonMetrics | null }
  );

  readonly metricRows = computed(() => personMetricMatrix(this.metrics()));
  readonly metricFetchKey = computed(() => metricFetchMessageKey(metricFetchKind(this.metrics()?.hIndex)));
  readonly metricFetchKindFailed = computed(() => metricFetchKind(this.metrics()?.hIndex) === 'failed');
  readonly language = this.i18n.currentLanguage;

  readonly displayName = computed(() => {
    const person = this.vm().person;
    if (!person) {
      return '';
    }
    const last = person.lastName?.trim() ?? '';
    const first = person.firstName?.trim() ?? '';
    if (last && first) {
      return `${last}, ${first}`;
    }
    return person.fullName?.trim() || last || first || '';
  });

  readonly headerSubtitle = computed(() => {
    const person = this.vm().person;
    if (!person) {
      return '';
    }
    return person.position ?? '';
  });

  readonly canSyncOrcid = computed(() => {
    const person = this.vm().person;
    return !!person?.orcid && this.orcidWorks()?.orcidValid !== false && this.auth.canAdminister('PEOPLE');
  });

  orcidHref(orcid: string): string {
    return `https://orcid.org/${orcid}`;
  }

  orcidFirstSeenKey(source: string | null | undefined): string {
    return orcidFirstSeenLabelKey(source);
  }

  issnOf(work: OrcidWork): string | null {
    const issn = work.externalIds.find((id) => id.type?.toLowerCase() === 'issn');
    return issn?.normalizedValue || issn?.value || null;
  }

  sourceLabel(row: { sourceTermEs?: string | null; sourceTermEn?: string | null; sourceUri?: string | null }): string {
    return this.language() === 'en'
      ? row.sourceTermEn || row.sourceTermEs || row.sourceUri || ''
      : row.sourceTermEs || row.sourceTermEn || row.sourceUri || '';
  }

  periodLabel(row: { categoryTermEs?: string | null; categoryTermEn?: string | null; categoryUri?: string | null }): string {
    return this.language() === 'en'
      ? row.categoryTermEn || row.categoryTermEs || row.categoryUri || ''
      : row.categoryTermEs || row.categoryTermEn || row.categoryUri || '';
  }

  affiliationName(item: PersonAffiliation): string {
    const preferred = this.language() === 'en' ? item.nameEn || item.nameEs : item.nameEs || item.nameEn;
    return preferred || this.i18n.translate('people.organizationUnavailable');
  }

  affiliationType(item: PersonAffiliation): string {
    return this.language() === 'en' ? item.typeEn || item.typeEs || '' : item.typeEs || item.typeEn || '';
  }

  identityLabel(identity: { type?: string | null; typeDiscriminator?: string | null }): string {
    return identity.type?.trim() || identity.typeDiscriminator?.trim() || '';
  }

  syncOrcid(): void {
    const person = this.vm().person;
    if (!person || this.orcidBusy() || !this.auth.canAdminister('PEOPLE')) {
      return;
    }
    this.orcidBusy.set(true);
    this.orcidMessage.set(null);
    this.peopleApi.syncOrcid(person.id).subscribe({
      next: () => {
        this.orcidBusy.set(false);
        this.orcidMessage.set('people.orcidSyncOk');
        this.orcidReload.update((value) => value + 1);
      },
      error: (error: HttpErrorResponse) => {
        this.orcidBusy.set(false);
        const code = error.error && typeof error.error === 'object' ? error.error.code : null;
        if (code === 'ORCID_NOT_CONFIGURED') {
          this.orcidMessage.set('sync.orcidNotConfigured');
          return;
        }
        if (code === 'ORCID_TABLES_UNAVAILABLE') {
          this.orcidMessage.set('sync.orcidTablesUnavailable');
          return;
        }
        if (code === 'SYNC_ALREADY_RUNNING') {
          this.orcidMessage.set('sync.alreadyRunningOrcid');
          return;
        }
        this.orcidMessage.set('people.orcidSyncFailed');
      }
    });
  }
}
