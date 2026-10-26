import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import { ApiPage } from './api.types';
import { environment } from '../../../../environments/environment';

export type SyncEntity = 'PEOPLE' | 'RESEARCH_OUTPUT' | 'JOURNALS' | 'PROJECTS' | 'ORGANIZATIONS' | 'ORCID' | 'UNPAYWALL' | 'CROSSREF' | 'OPENALEX' | 'DSPACE' | 'JOURNAL_METRICS' | 'RESEARCH_OUTPUT_METRICS' | 'PERSON_METRICS';

export const PURE_SYNC_ENTITIES: readonly SyncEntity[] = ['PEOPLE', 'RESEARCH_OUTPUT', 'JOURNALS', 'PROJECTS', 'ORGANIZATIONS'];

export function sameSyncEntity(left?: string | null, right?: string | null): boolean {
  return normalizeSyncEntity(left) === normalizeSyncEntity(right);
}

export function normalizeSyncEntity(entity?: string | null): string {
  const value = (entity ?? '').trim().toUpperCase();
  if (value === 'RESEARCH_OUTPUTS') {
    return 'RESEARCH_OUTPUT';
  }
  if (value === 'JOURNAL') {
    return 'JOURNALS';
  }
  if (value === 'PROJECT') {
    return 'PROJECTS';
  }
  if (value === 'ORGANIZATION') {
    return 'ORGANIZATIONS';
  }
  if (value === 'ORCID_WORKS') {
    return 'ORCID';
  }
  return value;
}

export function syncEntityLabelKey(entity?: string | null): string {
  const value = normalizeSyncEntity(entity);
  if (value === 'PEOPLE') {
    return 'navigation.people';
  }
  if (value === 'JOURNALS') {
    return 'navigation.journals';
  }
  if (value === 'PROJECTS') {
    return 'navigation.projects';
  }
  if (value === 'ORGANIZATIONS') {
    return 'navigation.organizations';
  }
  if (value === 'ORCID') {
    return 'sync.detail.orcidEntity';
  }
  if (value === 'UNPAYWALL') {
    return 'sync.detail.unpaywallEntity';
  }
  if (value === 'CROSSREF') {
    return 'sync.detail.crossrefEntity';
  }
  if (value === 'OPENALEX') {
    return 'sync.detail.openalexEntity';
  }
  if (value === 'DSPACE') {
    return 'sync.detail.dspaceEntity';
  }
  if (value === 'JOURNAL_METRICS') {
    return 'metrics.journalSyncEntity';
  }
  if (value === 'RESEARCH_OUTPUT_METRICS') {
    return 'metrics.outputSyncEntity';
  }
  if (value === 'PERSON_METRICS') {
    return 'metrics.personSyncEntity';
  }
  return 'navigation.researchOutput';
}

export function pureEntityPath(entity?: string | null): string {
  const value = normalizeSyncEntity(entity);
  if (value === 'PEOPLE') {
    return '/people';
  }
  if (value === 'JOURNALS') {
    return '/journals';
  }
  if (value === 'PROJECTS') {
    return '/projects';
  }
  if (value === 'ORGANIZATIONS') {
    return '/organizations';
  }
  return '/research-output';
}

export interface SyncRun {
  syncId: string;
  entity: SyncEntity | string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'COMPLETED_WITH_WARNINGS' | 'FAILED' | string;
  startedBy?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  heartbeatAt?: string | null;
  syncMode?: 'FULL' | 'INCREMENTAL' | string | null;
  totalExpected?: number | null;
  fetched?: number | null;
  processed: number;
  created: number;
  updated: number;
  unchanged: number;
  reactivated: number;
  markedInactive: number;
  errors: number;
  notFound?: number | null;
  oa?: number | null;
  nonOa?: number | null;
  errorCode?: string | null;
  message?: string | null;
  phase?: string | null;
  authorsTotal?: number | null;
  authorsProcessed?: number | null;
  authorsCreated?: number | null;
  authorsUpdated?: number | null;
  invalidCount?: number | null;
  recoveryAvailable?: boolean | null;
  recoveryType?: string | null;
  recoveryBlockedReason?: string | null;
  pendingAuthors?: number | null;
  sourceSyncExecutionId?: string | null;
}

export interface OpenAlexAuthorRecovery {
  sourceSyncExecutionId: string;
  recoveryExecutionId: string;
  status: string;
  operation: string;
  syncMode?: string | null;
  pendingAuthors?: number | null;
}

export interface ApiSyncModePolicy {
  entity?: string;
  supportedSyncModes?: string[] | null;
  availableSyncModes?: string[] | null;
  hasValidBaseline?: boolean | null;
  recommendedSyncMode?: string | null;
  lastSuccessfulFullSyncAt?: string | null;
  lastFullSyncAt?: string | null;
  lastExecution?: SyncRun | null;
}

export interface SyncConflictError {
  code?: string;
  message?: string;
  entity?: string;
  activeSyncId?: string;
  startedAt?: string;
  startedBy?: string;
}

@Injectable({ providedIn: 'root' })
export class SyncApiService {
  private readonly http = inject(HttpClient);

  startPeople(mode: 'FULL' | 'INCREMENTAL' = 'FULL'): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/pure/people`, {}, { params: { mode } });
  }

  startResearchOutputs(mode: 'FULL' | 'INCREMENTAL' = 'FULL'): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/pure/research-output`, {}, { params: { mode } });
  }

  startJournals(mode: 'FULL' | 'INCREMENTAL' = 'FULL'): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/pure/journals`, {}, { params: { mode } });
  }

  startProjects(mode: 'FULL' | 'INCREMENTAL' = 'FULL'): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/pure/projects`, {}, { params: { mode } });
  }

  startOrganizations(mode: 'FULL' | 'INCREMENTAL' = 'FULL'): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/pure/organizations`, {}, { params: { mode } });
  }

  startOrcid(mode: 'FULL' | 'INCREMENTAL' = 'INCREMENTAL'): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/orcid`, {}, { params: { mode } });
  }

  startUnpaywall(mode: 'FULL' | 'INCREMENTAL' = 'INCREMENTAL'): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/unpaywall`, {}, { params: { mode } });
  }

  startCrossref(mode: 'FULL' | 'INCREMENTAL' = 'INCREMENTAL'): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/crossref`, {}, {
      params: { mode }
    });
  }

  startOpenAlex(mode: 'FULL' | 'INCREMENTAL' = 'INCREMENTAL'): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/openalex`, {}, {
      params: { mode }
    });
  }

  recoverOpenAlexAuthors(sourceSyncExecutionId: string): Observable<OpenAlexAuthorRecovery> {
    return this.http.post<OpenAlexAuthorRecovery>(
      `${environment.apiBaseUrl}/sync/openalex/${encodeURIComponent(sourceSyncExecutionId)}/recover-authors`,
      {}
    );
  }

  startDspace(mode: 'FULL' | 'INCREMENTAL' = 'INCREMENTAL'): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/dspace`, {}, {
      params: { mode }
    });
  }

  startDspaceBitstreams(): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/dspace/bitstreams`, {});
  }

  startJournalMetrics(mode: 'FULL' | 'INCREMENTAL' = 'FULL'): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/pure/journal-metrics`, {}, { params: { mode } });
  }

  startResearchOutputMetrics(mode: 'FULL' | 'INCREMENTAL' = 'FULL'): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/pure/research-output-metrics`, {}, { params: { mode } });
  }

  startPersonMetrics(mode: 'FULL' | 'INCREMENTAL' = 'FULL'): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/pure/person-metrics`, {}, { params: { mode } });
  }

  get(syncId: string): Observable<SyncRun> {
    return this.http.get<SyncRun>(`${environment.apiBaseUrl}/sync/${syncId}`);
  }

  cancel(syncId: string): Observable<SyncRun> {
    return this.http.post<SyncRun>(`${environment.apiBaseUrl}/sync/${encodeURIComponent(syncId)}/cancel`, {});
  }

  history(entities: SyncEntity[]): Observable<SyncRun[]> {
    let params = new HttpParams();
    for (const entity of entities) {
      params = params.append('entity', entity);
    }
    return this.http.get<SyncRun[]>(`${environment.apiBaseUrl}/sync/history`, { params }).pipe(
      map((items) => items ?? [])
    );
  }

  latest(entity: SyncEntity): Observable<SyncRun | null> {
    return this.http
      .get<SyncRun>(`${environment.apiBaseUrl}/sync/latest`, {
        params: { entity },
        observe: 'response'
      })
      .pipe(
        map((response) => (response.status === 204 ? null : response.body)),
        catchError(() => of(null))
      );
  }

  policy(entity: SyncEntity): Observable<ApiSyncModePolicy | null> {
    return this.http
      .get<ApiSyncModePolicy>(`${environment.apiBaseUrl}/sync/policy`, {
        params: { entity }
      })
      .pipe(catchError(() => of(null)));
  }

  running(): Observable<SyncRun[]> {
    return this.http.get<SyncRun[]>(`${environment.apiBaseUrl}/sync/running`).pipe(
      map((items) => items ?? []),
      catchError(() => of([]))
    );
  }

  errors(syncId: string, page = 0, size = 25): Observable<SyncErrorPage> {
    return this.http.get<SyncErrorPage>(`${environment.apiBaseUrl}/sync/${syncId}/errors`, {
      params: { page, size }
    });
  }

  changes(
    syncId: string,
    changeType: SyncChangeTypeFilter = 'ALL',
    page = 0,
    size = 25
  ): Observable<ApiPage<SyncChangeGroup>> {
    return this.http
      .get<ApiPage<SyncChangeGroup>>(`${environment.apiBaseUrl}/sync/${syncId}/changes`, {
        params: { changeType, page, size }
      })
      .pipe(
        map(
          (pageResponse) =>
            pageResponse ?? { items: [], page, size, totalElements: 0, totalPages: 0 }
        )
      );
  }
}

export interface SyncErrorGroup {
  stage?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  count: number;
}

export interface SyncErrorItem {
  id?: number;
  syncId?: string;
  entity?: string;
  localRecordId?: number | null;
  pureId?: number | null;
  uuid?: string | null;
  stage?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
}

export interface SyncErrorPage {
  syncId?: string;
  totalElements: number;
  page: number;
  size: number;
  totalPages?: number;
  groups?: SyncErrorGroup[];
  items?: SyncErrorItem[];
}

export type SyncChangeTypeFilter = 'ALL' | 'CREATED' | 'UPDATED' | 'REACTIVATED' | 'INACTIVATED';

export interface SyncChangeLine {
  changeType?: string;
  scope?: string;
  description?: string | null;
  oldValue?: string | null;
  newValue?: string | null;
}

export interface SyncChangeGroup {
  localRecordId?: number | null;
  pureId?: number | null;
  uuid?: string | null;
  entity?: string;
  displayName?: string | null;
  changeType?: string;
  changes?: SyncChangeLine[];
}
