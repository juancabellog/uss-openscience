import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, of, tap } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  PagedResult,
  Person,
  PersonDetail,
  PersonFilters,
  PersonAffiliation,
  PersonIdentity,
  PersonMetrics,
  PersonQuery,
  OrcidWork,
  OrcidWorkExternalId,
  OrcidWorkGroup,
  PersonOrcidSyncResult,
  PersonOrcidWorks,
  PersonScopusIdentifiers,
  PersonStats,
  PureCorrectionResult
} from '../../models/openscience.models';
import {
  ApiPage,
  ApiPersonAffiliation,
  ApiPersonDetail,
  ApiPersonFilters,
  ApiOrcidWork,
  ApiOrcidWorkExternalId,
  ApiOrcidWorkGroup,
  ApiPersonOrcidSyncResult,
  ApiPersonOrcidWorks,
  ApiPersonScopusIdentifiers,
  ApiPersonStats,
  ApiPersonSummary
} from './api.types';

@Injectable({ providedIn: 'root' })
export class PersonApiService {
  private readonly http = inject(HttpClient);
  private readonly scopusIdentifiersCache = new Map<number, PersonScopusIdentifiers>();

  list(query: PersonQuery): Observable<PagedResult<Person>> {
    const page = Math.max((query.page ?? 1) - 1, 0);
    const size = query.pageSize ?? 25;
    const params = peopleFilterParams(query)
      .set('page', String(page))
      .set('size', String(size));
    return this.http
      .get<ApiPage<ApiPersonSummary>>(`${environment.apiBaseUrl}/people`, { params })
      .pipe(map(mapPeoplePage));
  }

  getById(id: number): Observable<PersonDetail> {
    return this.http
      .get<ApiPersonDetail>(`${environment.apiBaseUrl}/people/${id}`)
      .pipe(map(mapPersonDetail));
  }

  getMetrics(id: number): Observable<PersonMetrics> {
    return this.http.get<PersonMetrics>(`${environment.apiBaseUrl}/people/${id}/metrics`);
  }

  getOrcidWorks(id: number): Observable<PersonOrcidWorks> {
    return this.http
      .get<ApiPersonOrcidWorks>(`${environment.apiBaseUrl}/people/${id}/orcid-works`)
      .pipe(map(mapOrcidWorks));
  }

  syncOrcid(id: number): Observable<PersonOrcidSyncResult> {
    return this.http
      .post<ApiPersonOrcidSyncResult>(`${environment.apiBaseUrl}/people/${id}/orcid/sync`, {})
      .pipe(map(mapOrcidSyncResult));
  }

  getScopusIdentifiers(id: number): Observable<PersonScopusIdentifiers> {
    const cached = this.scopusIdentifiersCache.get(id);
    if (cached) {
      return of(cached);
    }
    return this.http
      .get<ApiPersonScopusIdentifiers>(`${environment.apiBaseUrl}/people/${id}/scopus-identifiers`)
      .pipe(
        map(mapScopusIdentifiers),
        tap((result) => this.scopusIdentifiersCache.set(id, result))
      );
  }

  invalidateScopusIdentifiers(id: number): void {
    this.scopusIdentifiersCache.delete(id);
  }

  removeScopusIdentifier(
    personId: number,
    identifierPureId: number,
    identifierValue: string | null
  ): Observable<PureCorrectionResult> {
    let params = new HttpParams();
    if (identifierValue) {
      params = params.set('identifierValue', identifierValue);
    }
    return this.http
      .delete<PureCorrectionResult>(
        `${environment.apiBaseUrl}/people/${personId}/scopus-identifiers/${identifierPureId}`,
        { params }
      )
      .pipe(tap(() => this.scopusIdentifiersCache.delete(personId)));
  }

  reconcileLocalScopusIdentifier(
    personId: number,
    identifierPureId: number
  ): Observable<PureCorrectionResult> {
    return this.http
      .post<PureCorrectionResult>(
        `${environment.apiBaseUrl}/people/${personId}/scopus-identifiers/${identifierPureId}/reconcile-local`,
        {}
      )
      .pipe(tap((result) => {
        if (result.status !== 'REMOTE_SUCCESS_LOCAL_SYNC_FAILED') {
          this.scopusIdentifiersCache.delete(personId);
        }
      }));
  }

  positions(): Observable<PersonFilters> {
    return this.http
      .get<ApiPersonFilters>(`${environment.apiBaseUrl}/people/filters`)
      .pipe(map(mapPersonFilters));
  }

  export(query: PersonQuery): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}/people/export.xlsx`, {
      params: peopleFilterParams(query),
      headers: new HttpHeaders({
        Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      }),
      responseType: 'blob',
      observe: 'response'
    });
  }

  stats(query?: PersonQuery): Observable<PersonStats> {
    const params = query
      ? peopleFilterParams({ ...query, sort: undefined, direction: undefined })
      : undefined;
    return this.http
      .get<ApiPersonStats>(`${environment.apiBaseUrl}/people/stats`, { params })
      .pipe(map(mapPersonStats));
  }
}

function mapPeoplePage(page: ApiPage<ApiPersonSummary>): PagedResult<Person> {
  return {
    items: (page.items ?? []).map(mapPerson),
    total: page.totalElements ?? 0,
    page: (page.page ?? 0) + 1,
    pageSize: page.size ?? 0
  };
}

function mapPerson(dto: ApiPersonSummary): Person {
  return {
    id: dto.id,
    pureId: dto.pureId ?? null,
    uuid: dto.uuid ?? null,
    firstName: dto.firstName ?? null,
    lastName: dto.lastName ?? null,
    fullName: dto.fullName ?? null,
    email: dto.email ?? null,
    position: dto.position ?? null,
    orcid: dto.orcid ?? null,
    orcidAuthenticated: dto.orcidAuthenticated ?? null,
    scopusIds: dto.scopusIds ?? [],
    scopusIdCount: dto.scopusIdCount ?? dto.scopusIds?.length ?? null,
    publicationCount: dto.publicationCount ?? null,
    organization: dto.organization ?? null,
    activeInPure: dto.activeInPure ?? null,
    createdBy: dto.createdBy ?? null,
    modifiedBy: dto.modifiedBy ?? null,
    scopusHindex: dto.scopusHindex ?? null,
    scopusCitations: dto.scopusCitations ?? null
  };
}

export function peopleFilterParams(query: PersonQuery): HttpParams {
  let params = new HttpParams();
  const search = query.search?.trim();
  if (search) {
    params = params.set('search', search);
  }
  if (query.cargo) {
    params = params.set('cargo', query.cargo);
  }
  if (query.population && query.population !== 'ALL') {
    params = params.set('population', query.population);
  }
  if (query.orcidStatus && query.orcidStatus !== 'ALL') {
    params = params.set('orcidStatus', query.orcidStatus);
  }
  if (query.scopusStatus && query.scopusStatus !== 'ALL') {
    params = params.set('scopusStatus', query.scopusStatus);
  }
  if (query.scopusCount != null) {
    params = params.set('scopusCount', String(query.scopusCount));
  }
  if (query.minDistinctScopusCount != null) {
    params = params.set('minDistinctScopusCount', String(query.minDistinctScopusCount));
  }
  if (query.sort) {
    params = params.set('sort', query.sort);
  }
  if (query.direction) {
    params = params.set('direction', query.direction.toUpperCase());
  }
  return params;
}

function mapPersonDetail(dto: ApiPersonDetail): PersonDetail {
  return {
    ...mapPerson(dto),
    gender: dto.gender ?? null,
    primaryId: dto.primaryId ?? null,
    identities: (dto.identities ?? []).map(mapIdentity),
    affiliations: (dto.affiliations ?? []).map(mapAffiliation),
    orcidSyncPending: dto.orcidSyncPending ?? null,
    lastOrcidSyncAt: dto.lastOrcidSyncAt ?? null,
    lastOrcidSyncStatus: dto.lastOrcidSyncStatus ?? null,
    orcidFirstSeenAt: dto.orcidFirstSeenAt ?? null,
    orcidFirstSeenSource: dto.orcidFirstSeenSource ?? null,
    openalexAuthorId: dto.openalexAuthorId ?? null,
    openalexDisplayName: dto.openalexDisplayName ?? null,
    openalexMatchStatus: dto.openalexMatchStatus ?? null,
    openalexUrl: dto.openalexUrl ?? null,
    openalexMatchingWorkCount: dto.openalexMatchingWorkCount ?? null,
    openalexConfirmed: dto.openalexConfirmed ?? null
  };
}

function mapOrcidWorks(dto: ApiPersonOrcidWorks): PersonOrcidWorks {
  const groups = (dto.groups ?? []).map(mapOrcidGroup);
  const works = groups.flatMap((group) => group.assertions);
  return {
    personId: dto.personId ?? 0,
    orcid: dto.orcid ?? null,
    orcidValid: dto.orcidValid ?? null,
    orcidSyncPending: dto.orcidSyncPending ?? null,
    lastOrcidSyncAt: dto.lastOrcidSyncAt ?? null,
    lastOrcidSyncStatus: dto.lastOrcidSyncStatus ?? null,
    workGroupCount: dto.workGroupCount ?? groups.length,
    workAssertionCount: dto.workAssertionCount ?? works.length,
    matchedByDoi: dto.matchedByDoi ?? 0,
    orcidOnly: dto.orcidOnly ?? 0,
    noDoi: dto.noDoi ?? 0,
    pureOnly: dto.pureOnly ?? 0,
    ambiguous: dto.ambiguous ?? 0,
    groups,
    works
  };
}

function mapOrcidGroup(item: ApiOrcidWorkGroup): OrcidWorkGroup {
  return {
    id: item.id ?? null,
    orcid: item.orcid ?? null,
    title: item.title ?? null,
    journalTitle: item.journalTitle ?? null,
    workType: item.workType ?? null,
    publicationYear: item.publicationYear ?? null,
    doi: item.doi ?? null,
    activeInOrcid: item.activeInOrcid ?? null,
    sourceCount: item.sourceCount ?? item.sources?.length ?? 0,
    sources: item.sources ?? [],
    matchStatus: item.matchStatus ?? null,
    externalIds: (item.externalIds ?? []).map(mapOrcidExternalId),
    assertions: (item.assertions ?? []).map(mapOrcidWork)
  };
}

function mapOrcidWork(item: ApiOrcidWork): OrcidWork {
  return {
    id: item.id ?? null,
    workGroupId: item.workGroupId ?? null,
    putCode: item.putCode ?? null,
    orcid: item.orcid ?? null,
    workType: item.workType ?? null,
    title: item.title ?? null,
    journalTitle: item.journalTitle ?? null,
    publicationYear: item.publicationYear ?? null,
    doi: item.doi ?? null,
    eid: item.eid ?? null,
    url: item.url ?? null,
    sourceName: item.sourceName ?? null,
    citationType: item.citationType ?? null,
    detailLoaded: item.detailLoaded ?? null,
    contributorCount: item.contributorCount ?? 0,
    activeInOrcid: item.activeInOrcid ?? null,
    matchStatus: item.matchStatus ?? null,
    externalIds: (item.externalIds ?? []).map(mapOrcidExternalId)
  };
}

function mapOrcidExternalId(item: ApiOrcidWorkExternalId): OrcidWorkExternalId {
  return {
    type: item.type ?? null,
    value: item.value ?? null,
    normalizedValue: item.normalizedValue ?? null,
    url: item.url ?? null,
    relationship: item.relationship ?? null
  };
}

function mapOrcidSyncResult(dto: ApiPersonOrcidSyncResult): PersonOrcidSyncResult {
  return {
    personId: dto.personId ?? 0,
    orcid: dto.orcid ?? null,
    status: dto.status ?? 'COMPLETED',
    received: dto.received ?? 0,
    created: dto.created ?? 0,
    updated: dto.updated ?? 0,
    unchanged: dto.unchanged ?? 0,
    reactivated: dto.reactivated ?? 0,
    markedInactive: dto.markedInactive ?? 0
  };
}

function mapScopusIdentifiers(dto: ApiPersonScopusIdentifiers): PersonScopusIdentifiers {
  return {
    personId: dto.personId ?? 0,
    count: dto.count ?? 0,
    distinctCount: dto.distinctCount ?? 0,
    matchedCount: dto.matchedCount ?? 0,
    items: (dto.items ?? []).map((item) => ({
      id: item.id ?? null,
      pureId: item.pureId ?? null,
      value: item.value ?? null,
      foundInScopus: item.foundInScopus === true,
      scopusAuthorId: item.scopusAuthorId ?? null,
      authorName: item.authorName ?? null,
      numberDocuments: item.numberDocuments ?? null,
      orcid: item.orcid ?? null
    }))
  };
}

function mapAffiliation(item: ApiPersonAffiliation): PersonAffiliation {
  return {
    personId: item.personId ?? null,
    pureId: item.pureId ?? null,
    organizationUuid: item.organizationUuid ?? null,
    organizationId: item.organizationId ?? null,
    nameEs: item.nameEs ?? null,
    nameEn: item.nameEn ?? null,
    typeEs: item.typeEs ?? null,
    typeEn: item.typeEn ?? null,
    organizationActiveInPure: item.organizationActiveInPure ?? null,
    resolved: item.resolved === true,
    employmentType: item.employmentType ?? null,
    email: item.email ?? null,
    startDate: item.startDate ?? null,
    endDate: item.endDate ?? null,
    primaryAssociation: item.primaryAssociation ?? null,
    contractType: item.contractType ?? null,
    fte: item.fte ?? null,
    jobDescription: item.jobDescription ?? null,
    jobTitle: item.jobTitle ?? null,
    staffType: item.staffType ?? null
  };
}

function mapIdentity(item: { typeDiscriminator?: string | null; type?: string | null; value?: string | null }): PersonIdentity {
  return {
    typeDiscriminator: item.typeDiscriminator ?? null,
    type: item.type ?? null,
    value: item.value ?? null
  };
}

function mapPersonFilters(dto: ApiPersonFilters): PersonFilters {
  return {
    positions: dto.positions ?? [],
    withoutPositionAvailable: dto.withoutPositionAvailable === true,
    scopusIdCounts: dto.scopusIdCounts ?? []
  };
}

function mapPersonStats(dto: ApiPersonStats): PersonStats {
  const distribution = (dto.scopus?.distribution ?? []).map((bucket) => ({
    count: bucket.count ?? 0,
    people: bucket.people ?? 0
  }));
  return {
    total: dto.total ?? 0,
    totalPeople: dto.totalPeople ?? dto.total ?? 0,
    researchers: dto.researchers ?? 0,
    withoutPosition: dto.withoutPosition ?? 0,
    scopus: {
      without: dto.scopus?.without ?? 0,
      one: dto.scopus?.one ?? 0,
      two: dto.scopus?.two ?? 0,
      threeOrMore: dto.scopus?.threeOrMore ?? 0,
      maxCount: dto.scopus?.maxCount ?? 0,
      distribution
    },
    withScopusHindex: dto.withScopusHindex ?? 0
  };
}
