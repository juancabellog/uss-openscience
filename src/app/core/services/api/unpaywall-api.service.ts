import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  PagedResult,
  UnpaywallFilters,
  UnpaywallQuery,
  UnpaywallStats,
  UnpaywallWorkDetail,
  UnpaywallWorkSummary
} from '../../models/openscience.models';
import { ApiPage } from './api.types';

@Injectable({ providedIn: 'root' })
export class UnpaywallApiService {
  private readonly http = inject(HttpClient);

  stats(): Observable<UnpaywallStats> {
    return this.http.get<UnpaywallStats>(`${environment.apiBaseUrl}/unpaywall/stats`);
  }

  filters(): Observable<UnpaywallFilters> {
    return this.http.get<UnpaywallFilters>(`${environment.apiBaseUrl}/unpaywall/filters`);
  }

  list(query: UnpaywallQuery): Observable<PagedResult<UnpaywallWorkSummary>> {
    return this.http
      .get<ApiPage<UnpaywallWorkSummary>>(`${environment.apiBaseUrl}/unpaywall/works`, {
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

  detail(id: number): Observable<UnpaywallWorkDetail> {
    return this.http.get<UnpaywallWorkDetail>(`${environment.apiBaseUrl}/unpaywall/works/${id}`);
  }

  export(query: UnpaywallQuery): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}/unpaywall/works/export.xlsx`, {
      params: toParams({ ...query, page: 1, pageSize: 20000 }),
      responseType: 'blob',
      observe: 'response'
    });
  }
}

function toParams(query: UnpaywallQuery): HttpParams {
  let params = new HttpParams();
  const set = (key: string, value: string | number | boolean | undefined) => {
    if (value === undefined || value === null || value === '') {
      return;
    }
    params = params.set(key, String(value));
  };
  set('search', query.search);
  set('oaStatus', query.oaStatus);
  set('hostType', query.hostType);
  set('license', query.license);
  set('version', query.version);
  set('isOa', query.isOa);
  set('repositoryCopy', query.repositoryCopy);
  set('outcome', query.outcome);
  set('sort', query.sort);
  set('direction', query.direction);
  set('page', query.page);
  set('size', query.pageSize);
  return params;
}
