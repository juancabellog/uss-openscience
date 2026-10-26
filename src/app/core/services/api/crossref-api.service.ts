import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  CrossrefFilters,
  CrossrefQuery,
  CrossrefStats,
  CrossrefWorkDetail,
  CrossrefWorkSummary,
  PagedResult
} from '../../models/openscience.models';
import { ApiPage } from './api.types';

@Injectable({ providedIn: 'root' })
export class CrossrefApiService {
  private readonly http = inject(HttpClient);

  stats(): Observable<CrossrefStats> {
    return this.http.get<CrossrefStats>(`${environment.apiBaseUrl}/crossref/stats`);
  }

  filters(): Observable<CrossrefFilters> {
    return this.http.get<CrossrefFilters>(`${environment.apiBaseUrl}/crossref/filters`);
  }

  list(query: CrossrefQuery): Observable<PagedResult<CrossrefWorkSummary>> {
    return this.http
      .get<ApiPage<CrossrefWorkSummary>>(`${environment.apiBaseUrl}/crossref/works`, {
        params: toParams(query)
      })
      .pipe(
        map((page) => ({
          items: page.items ?? [],
          total: page.totalElements ?? 0,
          page: (page.page ?? 0) + 1,
          pageSize: page.size ?? query.pageSize ?? 25
        }))
      );
  }

  detail(id: number): Observable<CrossrefWorkDetail> {
    return this.http.get<CrossrefWorkDetail>(`${environment.apiBaseUrl}/crossref/works/${id}`);
  }

  byResearchOutput(id: number): Observable<CrossrefWorkSummary[]> {
    return this.http.get<CrossrefWorkSummary[]>(`${environment.apiBaseUrl}/research-output/${id}/crossref`);
  }

  export(query: CrossrefQuery): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}/crossref/works/export.xlsx`, {
      params: toParams({ ...query, page: 1, pageSize: 20000 }),
      responseType: 'blob',
      observe: 'response'
    });
  }
}

function toParams(query: CrossrefQuery): HttpParams {
  let params = new HttpParams();
  const set = (key: string, value: string | number | boolean | undefined) => {
    if (value === undefined || value === null || value === '') {
      return;
    }
    params = params.set(key, String(value));
  };
  set('search', query.search);
  set('status', query.status);
  set('workType', query.workType);
  set('hasOrcid', query.hasOrcid);
  set('hasAffiliation', query.hasAffiliation);
  set('hasRor', query.hasRor);
  set('hasFunding', query.hasFunding);
  set('hasLicense', query.hasLicense);
  set('hasUpdate', query.hasUpdate);
  set('comparison', query.comparison);
  set('sort', query.sort);
  set('direction', query.direction);
  set('page', query.page);
  set('size', query.pageSize);
  return params;
}
