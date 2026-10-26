import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  OpenAlexAuthorQuery,
  OpenAlexAuthorSummary,
  OpenAlexFilters,
  OpenAlexMatchDetail,
  OpenAlexMatchQuery,
  OpenAlexMatchSummary,
  OpenAlexStats,
  OpenAlexWorkQuery,
  OpenAlexWorkSummary,
  PagedResult
} from '../../models/openscience.models';
import { ApiPage } from './api.types';
import { SyncRun } from './sync-api.service';

@Injectable({ providedIn: 'root' })
export class OpenAlexApiService {
  private readonly http = inject(HttpClient);

  stats(): Observable<OpenAlexStats> {
    return this.http.get<OpenAlexStats>(`${environment.apiBaseUrl}/openalex/stats`);
  }

  history(): Observable<SyncRun[]> {
    return this.http.get<SyncRun[]>(`${environment.apiBaseUrl}/openalex/history`);
  }

  filters(): Observable<OpenAlexFilters> {
    return this.http.get<OpenAlexFilters>(`${environment.apiBaseUrl}/openalex/filters`);
  }

  works(query: OpenAlexWorkQuery): Observable<PagedResult<OpenAlexWorkSummary>> {
    return this.http
      .get<ApiPage<OpenAlexWorkSummary>>(`${environment.apiBaseUrl}/openalex/works`, {
        params: workParams(query)
      })
      .pipe(map(toPage(query.pageSize)));
  }

  authors(query: OpenAlexAuthorQuery): Observable<PagedResult<OpenAlexAuthorSummary>> {
    return this.http
      .get<ApiPage<OpenAlexAuthorSummary>>(`${environment.apiBaseUrl}/openalex/authors`, {
        params: authorParams(query)
      })
      .pipe(map(toPage(query.pageSize)));
  }

  matches(query: OpenAlexMatchQuery): Observable<PagedResult<OpenAlexMatchSummary>> {
    return this.http
      .get<ApiPage<OpenAlexMatchSummary>>(`${environment.apiBaseUrl}/openalex/matches`, {
        params: matchParams(query)
      })
      .pipe(map(toPage(query.pageSize)));
  }

  matchDetail(
    id: number,
    query: { workPage?: number; workSize?: number; workSearch?: string } = {}
  ): Observable<OpenAlexMatchDetail> {
    return this.http.get<OpenAlexMatchDetail>(`${environment.apiBaseUrl}/openalex/matches/${id}`, {
      params: setParams({
        workPage: query.workPage,
        workSize: query.workSize,
        workSearch: query.workSearch
      })
    });
  }

  confirm(id: number): Observable<OpenAlexMatchDetail> {
    return this.http.post<OpenAlexMatchDetail>(`${environment.apiBaseUrl}/openalex/matches/${id}/confirm`, {});
  }

  reject(id: number): Observable<OpenAlexMatchDetail> {
    return this.http.post<OpenAlexMatchDetail>(`${environment.apiBaseUrl}/openalex/matches/${id}/reject`, {});
  }

  syncOrcid(id: number): Observable<{ status?: string | null; message?: string | null }> {
    return this.http.post<{ status?: string | null; message?: string | null }>(
      `${environment.apiBaseUrl}/openalex/matches/${id}/sync-orcid`,
      {}
    );
  }

  export(query: OpenAlexMatchQuery): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}/openalex/matches/export.xlsx`, {
      params: matchParams({ ...query, page: 1, pageSize: 20000 }),
      responseType: 'blob',
      observe: 'response'
    });
  }
}

function toPage<T>(pageSize?: number) {
  return (page: ApiPage<T>): PagedResult<T> => ({
    items: page.items ?? [],
    total: page.totalElements ?? 0,
    page: (page.page ?? 0) + 1,
    pageSize: page.size ?? pageSize ?? 25
  });
}

function workParams(query: OpenAlexWorkQuery): HttpParams {
  return setParams({
    search: query.search,
    status: query.status,
    workType: query.workType,
    sort: query.sort,
    direction: query.direction,
    page: query.page,
    size: query.pageSize
  });
}

function authorParams(query: OpenAlexAuthorQuery): HttpParams {
  return setParams({
    search: query.search,
    sort: query.sort,
    direction: query.direction,
    page: query.page,
    size: query.pageSize
  });
}

function matchParams(query: OpenAlexMatchQuery): HttpParams {
  return setParams({
    search: query.search,
    status: query.status,
    position: query.position,
    hasOrcid: query.hasOrcid,
    ussAffiliation: query.ussAffiliation,
    minWorks: query.minWorks,
    sort: query.sort,
    direction: query.direction,
    page: query.page,
    size: query.pageSize
  });
}

function setParams(values: Record<string, string | number | boolean | undefined>): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    params = params.set(key, String(value));
  }
  return params;
}
