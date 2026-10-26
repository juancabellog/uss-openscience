import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  DirectoryImportResult,
  DirectoryImportSummary,
  DirectoryIssue,
  DirectoryPerson,
  DirectoryPersonDetail,
  DirectoryQuery,
  DirectoryStats,
  PagedResult
} from '../../models/openscience.models';
import { ApiPage } from './api.types';

interface ApiDirectoryPerson {
  id: number;
  uuid?: string | null;
  displayName?: string | null;
  email?: string | null;
  campusName?: string | null;
  faculty?: string | null;
  positionTitle?: string | null;
  activeInDirectory?: boolean | null;
  linkedToPure?: boolean | null;
}

interface ApiDirectoryPersonDetail extends ApiDirectoryPerson {
  givenName?: string | null;
  middleName?: string | null;
  familyName?: string | null;
  secondFamilyName?: string | null;
  facultyRaw?: string | null;
  lastSeenAt?: string | null;
  removedAt?: string | null;
  emails?: Array<{
    email?: string | null;
    kind?: string | null;
    source?: string | null;
    active?: boolean | null;
  }> | null;
}

interface ApiDirectoryStats {
  total?: number;
  active?: number;
  inactive?: number;
  withPureLink?: number;
}

interface ApiDirectoryIssue {
  sourceRowNumber?: number;
  code?: string | null;
  leftPersonId?: number | null;
  rightPersonId?: number | null;
  resolved?: boolean | null;
}

interface ApiDirectoryImportResult {
  dryRun?: boolean;
  importPublicId?: string | null;
  filename?: string | null;
  sheetName?: string | null;
  status?: string | null;
  rowCount?: number;
  created?: number;
  updated?: number;
  unchanged?: number;
  reactivated?: number;
  rejected?: number;
  wouldInactivate?: number;
  inactivated?: number;
  issueCount?: number;
  censusReliable?: boolean;
  unmappedHeaders?: string[] | null;
  issues?: ApiDirectoryIssue[] | null;
}

interface ApiDirectoryImportSummary {
  id: number;
  publicId?: string | null;
  filename?: string | null;
  status?: string | null;
  importedBy?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  rowCount?: number;
  createdCount?: number;
  updatedCount?: number;
  unchangedCount?: number;
  reactivatedCount?: number;
  inactivatedCount?: number;
  issueCount?: number;
  expectedCount?: number;
  processedCount?: number;
  errorMessage?: string | null;
  heartbeatAt?: string | null;
  owned?: boolean;
  interrupted?: boolean;
}

@Injectable({ providedIn: 'root' })
export class DirectoryApiService {
  private readonly http = inject(HttpClient);

  list(query: DirectoryQuery): Observable<PagedResult<DirectoryPerson>> {
    const page = Math.max((query.page ?? 1) - 1, 0);
    const size = query.pageSize ?? 25;
    let params = new HttpParams().set('page', String(page)).set('size', String(size));
    if (query.search?.trim()) {
      params = params.set('search', query.search.trim());
    }
    if (query.presence) {
      params = params.set('presence', query.presence);
    }
    return this.http.get<ApiPage<ApiDirectoryPerson>>(`${environment.apiBaseUrl}/directory`, { params }).pipe(
      map((pageDto) => ({
        items: (pageDto.items ?? []).map(mapPerson),
        total: pageDto.totalElements ?? 0,
        page: (pageDto.page ?? 0) + 1,
        pageSize: pageDto.size ?? 0
      }))
    );
  }

  stats(query?: DirectoryQuery): Observable<DirectoryStats> {
    let params = new HttpParams();
    if (query?.search?.trim()) {
      params = params.set('search', query.search.trim());
    }
    if (query?.presence) {
      params = params.set('presence', query.presence);
    }
    return this.http.get<ApiDirectoryStats>(`${environment.apiBaseUrl}/directory/stats`, { params }).pipe(
      map((dto) => ({
        total: dto.total ?? 0,
        active: dto.active ?? 0,
        inactive: dto.inactive ?? 0,
        withPureLink: dto.withPureLink ?? 0
      }))
    );
  }

  getById(id: number): Observable<DirectoryPersonDetail> {
    return this.http.get<ApiDirectoryPersonDetail>(`${environment.apiBaseUrl}/directory/${id}`).pipe(map(mapDetail));
  }

  imports(): Observable<DirectoryImportSummary[]> {
    return this.http.get<ApiDirectoryImportSummary[]>(`${environment.apiBaseUrl}/directory/imports`).pipe(
      map((items) => (items ?? []).map(mapImport))
    );
  }

  issues(importId: number): Observable<DirectoryIssue[]> {
    return this.http.get<ApiDirectoryIssue[]>(`${environment.apiBaseUrl}/directory/imports/${importId}/issues`).pipe(
      map((items) => (items ?? []).map(mapIssue))
    );
  }

  recoverImport(importId: number): Observable<DirectoryImportSummary> {
    return this.http
      .post<ApiDirectoryImportSummary>(`${environment.apiBaseUrl}/directory/imports/${importId}/recover`, {})
      .pipe(map(mapImport));
  }

  dryRun(file: File): Observable<DirectoryImportResult> {
    return this.upload(file, true);
  }

  importFile(file: File): Observable<DirectoryImportResult> {
    return this.upload(file, false);
  }

  private upload(file: File, dryRun: boolean): Observable<DirectoryImportResult> {
    const headers = new HttpHeaders({
      'Content-Type': file.type || 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'X-Filename': file.name
    });
    const url = dryRun
      ? `${environment.apiBaseUrl}/directory/imports/dry-run`
      : `${environment.apiBaseUrl}/directory/imports`;
    return this.http.post<ApiDirectoryImportResult>(url, file, { headers }).pipe(map(mapResult));
  }
}

function mapPerson(dto: ApiDirectoryPerson): DirectoryPerson {
  return {
    id: dto.id,
    uuid: dto.uuid ?? null,
    displayName: dto.displayName ?? null,
    email: dto.email ?? null,
    campusName: dto.campusName ?? null,
    faculty: dto.faculty ?? null,
    positionTitle: dto.positionTitle ?? null,
    activeInDirectory: dto.activeInDirectory === true,
    linkedToPure: dto.linkedToPure === true
  };
}

function mapDetail(dto: ApiDirectoryPersonDetail): DirectoryPersonDetail {
  return {
    ...mapPerson(dto),
    givenName: dto.givenName ?? null,
    middleName: dto.middleName ?? null,
    familyName: dto.familyName ?? null,
    secondFamilyName: dto.secondFamilyName ?? null,
    facultyRaw: dto.facultyRaw ?? null,
    lastSeenAt: dto.lastSeenAt ?? null,
    removedAt: dto.removedAt ?? null,
    emails: (dto.emails ?? []).map((item) => ({
      email: item.email ?? null,
      kind: item.kind ?? null,
      source: item.source ?? null,
      active: item.active === true
    }))
  };
}

function mapIssue(dto: ApiDirectoryIssue): DirectoryIssue {
  return {
    sourceRowNumber: dto.sourceRowNumber ?? 0,
    code: dto.code ?? '',
    leftPersonId: dto.leftPersonId ?? null,
    rightPersonId: dto.rightPersonId ?? null,
    resolved: dto.resolved === true
  };
}

function mapResult(dto: ApiDirectoryImportResult): DirectoryImportResult {
  return {
    dryRun: dto.dryRun === true,
    importPublicId: dto.importPublicId ?? null,
    filename: dto.filename ?? null,
    sheetName: dto.sheetName ?? null,
    status: dto.status ?? null,
    rowCount: dto.rowCount ?? 0,
    created: dto.created ?? 0,
    updated: dto.updated ?? 0,
    unchanged: dto.unchanged ?? 0,
    reactivated: dto.reactivated ?? 0,
    rejected: dto.rejected ?? 0,
    wouldInactivate: dto.wouldInactivate ?? 0,
    inactivated: dto.inactivated ?? 0,
    issueCount: dto.issueCount ?? 0,
    censusReliable: dto.censusReliable === true,
    unmappedHeaders: dto.unmappedHeaders ?? [],
    issues: (dto.issues ?? []).map(mapIssue)
  };
}

function mapImport(dto: ApiDirectoryImportSummary): DirectoryImportSummary {
  return {
    id: dto.id,
    publicId: dto.publicId ?? null,
    filename: dto.filename ?? null,
    status: dto.status ?? null,
    importedBy: dto.importedBy ?? null,
    startedAt: dto.startedAt ?? null,
    finishedAt: dto.finishedAt ?? null,
    rowCount: dto.rowCount ?? 0,
    createdCount: dto.createdCount ?? 0,
    updatedCount: dto.updatedCount ?? 0,
    unchangedCount: dto.unchangedCount ?? 0,
    reactivatedCount: dto.reactivatedCount ?? 0,
    inactivatedCount: dto.inactivatedCount ?? 0,
    issueCount: dto.issueCount ?? 0,
    expectedCount: dto.expectedCount ?? 0,
    processedCount: dto.processedCount ?? 0,
    errorMessage: dto.errorMessage ?? null,
    heartbeatAt: dto.heartbeatAt ?? null,
    owned: dto.owned === true,
    interrupted: dto.interrupted === true
  };
}
