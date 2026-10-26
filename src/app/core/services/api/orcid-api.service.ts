import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  OrcidCandidate,
  OrcidCandidateEvidence,
  OrcidDiscoveryApplyResult,
  OrcidDiscoveryExecution,
  OrcidDiscoveryPerson,
  OrcidDiscoverySelectedItem,
  OrcidDiscoveryStats,
  OrcidStats,
  PagedResult
} from '../../models/openscience.models';
import {
  ApiOrcidCandidate,
  ApiOrcidCandidateEvidence,
  ApiOrcidDiscoveryApplyResult,
  ApiOrcidDiscoveryExecution,
  ApiOrcidDiscoveryPerson,
  ApiOrcidDiscoverySelectedItem,
  ApiOrcidDiscoveryStats,
  ApiOrcidStats,
  ApiPage
} from './api.types';

@Injectable({ providedIn: 'root' })
export class OrcidApiService {
  private readonly http = inject(HttpClient);

  stats(): Observable<OrcidStats> {
    return this.http.get<ApiOrcidStats>(`${environment.apiBaseUrl}/orcid/stats`).pipe(
      map((dto) => ({
        configured: dto.configured === true,
        tablesAvailable: dto.tablesAvailable === true,
        peopleWithOrcid: dto.peopleWithOrcid ?? 0,
        peopleSynced: dto.peopleSynced ?? 0,
        workGroupCount: dto.workGroupCount ?? dto.works ?? 0,
        workAssertionCount: dto.workAssertionCount ?? 0,
        works: dto.workGroupCount ?? dto.works ?? 0,
        lastProcessed: dto.lastProcessed ?? null,
        lastErrors: dto.lastErrors ?? null,
        lastExecution: dto.lastExecution ?? null,
        lastStatus: dto.lastStatus ?? null
      }))
    );
  }

  discoveryStats(): Observable<OrcidDiscoveryStats> {
    return this.http.get<ApiOrcidDiscoveryStats>(`${environment.apiBaseUrl}/orcid/discovery/stats`).pipe(
      map((dto) => ({
        people: dto.people ?? 0,
        withOrcid: dto.withOrcid ?? 0,
        withoutOrcid: dto.withoutOrcid ?? 0,
        withCandidates: dto.withCandidates ?? 0,
        strongMatches: dto.strongMatches ?? 0,
        manualReview: dto.manualReview ?? 0,
        noCandidate: dto.noCandidate ?? 0,
        conflicts: dto.conflicts ?? 0,
        searchMaxCandidates: dto.searchMaxCandidates ?? 20
      }))
    );
  }

  latestDiscovery(): Observable<OrcidDiscoveryExecution | null> {
    return this.http
      .get<ApiOrcidDiscoveryExecution>(`${environment.apiBaseUrl}/orcid/discovery/latest`, {
        observe: 'response'
      })
      .pipe(map((response) => (response.status === 204 || !response.body ? null : mapExecution(response.body))));
  }

  discoveryHistory(): Observable<OrcidDiscoveryExecution[]> {
    return this.http
      .get<ApiOrcidDiscoveryExecution[]>(`${environment.apiBaseUrl}/orcid/discovery/history`)
      .pipe(map((items) => (items ?? []).map(mapExecution)));
  }

  startDiscovery(): Observable<OrcidDiscoveryExecution> {
    return this.http
      .post<ApiOrcidDiscoveryExecution>(`${environment.apiBaseUrl}/orcid/discovery`, {})
      .pipe(map(mapExecution));
  }

  getDiscovery(id: number): Observable<OrcidDiscoveryExecution> {
    return this.http
      .get<ApiOrcidDiscoveryExecution>(`${environment.apiBaseUrl}/orcid/discovery/${id}`)
      .pipe(map(mapExecution));
  }

  discoveryPeople(
    executionId: number,
    query: {
      status?: string;
      cargo?: string;
      search?: string;
      sort?: string;
      direction?: string;
      page?: number;
      pageSize?: number;
    }
  ): Observable<PagedResult<OrcidDiscoveryPerson>> {
    const page = Math.max(0, (query.page ?? 1) - 1);
    const size = query.pageSize ?? 25;
    return this.http
      .get<ApiPage<ApiOrcidDiscoveryPerson>>(`${environment.apiBaseUrl}/orcid/discovery/${executionId}/people`, {
        params: {
          status: query.status || 'ALL',
          cargo: query.cargo || '',
          search: query.search || '',
          sort: query.sort || 'name',
          direction: query.direction || 'asc',
          page,
          size
        }
      })
      .pipe(
        map((dto) => ({
          items: (dto.items ?? []).map(mapPerson),
          total: dto.totalElements ?? 0,
          page: (dto.page ?? page) + 1,
          pageSize: dto.size ?? size
        }))
      );
  }

  discoveryCargos(executionId: number): Observable<string[]> {
    return this.http.get<string[]>(`${environment.apiBaseUrl}/orcid/discovery/${executionId}/cargos`);
  }

  discoveryPerson(executionId: number, personId: number): Observable<OrcidDiscoveryPerson> {
    return this.http
      .get<ApiOrcidDiscoveryPerson>(`${environment.apiBaseUrl}/orcid/discovery/${executionId}/people/${personId}`)
      .pipe(map(mapPerson));
  }

  selectCandidate(
    executionId: number,
    personId: number,
    candidateId: number | null,
    queued?: boolean | null
  ): Observable<OrcidDiscoveryPerson> {
    return this.http
      .post<ApiOrcidDiscoveryPerson>(
        `${environment.apiBaseUrl}/orcid/discovery/${executionId}/people/${personId}/select`,
        { candidateId, queued: queued ?? null }
      )
      .pipe(map(mapPerson));
  }

  queuedForApply(executionId: number): Observable<OrcidDiscoverySelectedItem[]> {
    return this.http
      .get<ApiOrcidDiscoverySelectedItem[]>(`${environment.apiBaseUrl}/orcid/discovery/${executionId}/selected`)
      .pipe(map((items) => (items ?? []).map(mapQueued)));
  }

  selectStrong(executionId: number): Observable<number> {
    return this.http
      .post<{ updated?: number }>(`${environment.apiBaseUrl}/orcid/discovery/${executionId}/select-strong`, {})
      .pipe(map((dto) => dto.updated ?? 0));
  }

  recalculateDiscovery(executionId: number): Observable<OrcidDiscoveryExecution> {
    return this.http
      .post<ApiOrcidDiscoveryExecution>(`${environment.apiBaseUrl}/orcid/discovery/${executionId}/recalculate`, {})
      .pipe(map(mapExecution));
  }

  applySelected(executionId: number, personIds: number[]): Observable<OrcidDiscoveryApplyResult> {
    return this.http
      .post<ApiOrcidDiscoveryApplyResult>(`${environment.apiBaseUrl}/orcid/discovery/${executionId}/apply`, {
        personIds
      })
      .pipe(
        map((dto) => ({
          selected: dto.selected ?? 0,
          succeeded: dto.succeeded ?? 0,
          failed: dto.failed ?? 0,
          items: (dto.items ?? []).map((item) => ({
            status: item.status ?? '',
            localParentId: item.localParentId ?? null,
            parentUuid: item.parentUuid ?? null,
            childValue: item.childValue ?? null,
            displayName: item.displayName ?? null,
            pureId: item.pureId ?? null,
            message: item.message ?? null
          }))
        }))
      );
  }

  reconcileLocal(executionId: number, personId: number): Observable<OrcidDiscoveryApplyItemSafe> {
    return this.http.post<OrcidDiscoveryApplyItemSafe>(
      `${environment.apiBaseUrl}/orcid/discovery/${executionId}/people/${personId}/reconcile-local`,
      {}
    );
  }
}

export interface OrcidDiscoveryApplyItemSafe {
  status?: string;
  message?: string | null;
}

function mapExecution(dto: ApiOrcidDiscoveryExecution): OrcidDiscoveryExecution {
  return {
    id: dto.id ?? 0,
    status: dto.status ?? '',
    startedAt: dto.startedAt ?? null,
    finishedAt: dto.finishedAt ?? null,
    startedBy: dto.startedBy ?? null,
    totalPeople: dto.totalPeople ?? 0,
    processedPeople: dto.processedPeople ?? 0,
    peopleWithCandidates: dto.peopleWithCandidates ?? 0,
    strongMatchCount: dto.strongMatchCount ?? 0,
    manualReviewCount: dto.manualReviewCount ?? 0,
    noCandidateCount: dto.noCandidateCount ?? 0,
    tooManyCount: dto.tooManyCount ?? 0,
    conflictCount: dto.conflictCount ?? 0,
    errorCount: dto.errorCount ?? 0
  };
}

function mapPerson(dto: ApiOrcidDiscoveryPerson): OrcidDiscoveryPerson {
  return {
    id: dto.id ?? 0,
    personId: dto.personId ?? 0,
    pureId: dto.pureId ?? null,
    uuid: dto.uuid ?? null,
    firstName: dto.firstName ?? null,
    lastName: dto.lastName ?? null,
    fullName: dto.fullName ?? `${dto.lastName ?? ''}, ${dto.firstName ?? ''}`.trim(),
    cargo: dto.cargo ?? null,
    pureOrcid: dto.pureOrcid ?? null,
    foundOrcid: dto.foundOrcid ?? dto.selectedOrcid ?? null,
    selectedOrcid: dto.selectedOrcid ?? null,
    bestCandidateId: dto.bestCandidateId ?? null,
    uniqueStrongCandidateId: dto.uniqueStrongCandidateId ?? null,
    personStatus: dto.personStatus ?? '',
    statusReason: dto.statusReason ?? (dto.personStatus === 'TOO_MANY_CANDIDATES' ? 'TOO_MANY_CANDIDATES' : null),
    candidateCount: dto.candidateCount ?? 0,
    searchHitCount: dto.searchHitCount ?? dto.candidateCount ?? 0,
    doiMatchCount: dto.doiMatchCount ?? 0,
    selectedCandidateId: dto.selectedCandidateId ?? null,
    selectionOrigin: dto.selectionOrigin ?? null,
    selectedBy: dto.selectedBy ?? null,
    selectedAt: dto.selectedAt ?? null,
    searchGivenName: dto.searchGivenName ?? null,
    searchFamilyName: dto.searchFamilyName ?? null,
    lastError: dto.lastError ?? null,
    selectable: dto.selectable === true,
    selected: dto.selected === true,
    selectionDisabledReason: dto.selectionDisabledReason ?? null,
    candidates: (dto.candidates ?? []).map(mapCandidate)
  };
}

function mapQueued(dto: ApiOrcidDiscoverySelectedItem): OrcidDiscoverySelectedItem {
  return {
    personId: dto.personId ?? 0,
    displayName: dto.displayName ?? null,
    pureId: dto.pureId ?? null,
    orcid: dto.orcid ?? null,
    candidateId: dto.candidateId ?? null,
    personStatus: dto.personStatus ?? null,
    selectionOrigin: dto.selectionOrigin ?? null,
    queued: dto.queued === true
  };
}

function mapCandidate(dto: ApiOrcidCandidate): OrcidCandidate {
  return {
    id: dto.id ?? 0,
    orcid: dto.orcid ?? '',
    score: dto.score ?? 0,
    matchLevel: dto.matchLevel ?? '',
    status: dto.status ?? '',
    nameMatch: dto.nameMatch ?? null,
    doiMatchCount: dto.doiMatchCount ?? 0,
    authorMatch: dto.authorMatch ?? null,
    positionMatch: dto.positionMatch ?? null,
    alreadyUsedPersonId: dto.alreadyUsedPersonId ?? null,
    evidence: (dto.evidence ?? []).map(mapEvidence)
  };
}

function mapEvidence(dto: ApiOrcidCandidateEvidence): OrcidCandidateEvidence {
  return {
    id: dto.id ?? 0,
    researchOutputId: dto.researchOutputId ?? null,
    doi: dto.doi ?? null,
    localAuthor: dto.localAuthor ?? null,
    localPosition: dto.localPosition ?? null,
    orcidAuthor: dto.orcidAuthor ?? null,
    orcidPosition: dto.orcidPosition ?? null,
    nameMatch: dto.nameMatch ?? null,
    positionMatch: dto.positionMatch ?? null,
    note: dto.note ?? null,
    personFullName: dto.personFullName ?? null,
    contributorFullName: dto.contributorFullName ?? null,
    personFirstName: dto.personFirstName ?? null,
    personLastName: dto.personLastName ?? null,
    contributorFirstName: dto.contributorFirstName ?? null,
    contributorLastName: dto.contributorLastName ?? null,
    namePartitionDifferent: dto.namePartitionDifferent ?? null,
    reasonCodes: dto.reasonCodes ?? []
  };
}
