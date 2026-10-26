import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  DspaceCollectionList,
  DspaceCommunityList,
  DspaceIncident,
  DspaceItemDetail,
  DspaceItemList,
  DspaceItemQuery,
  DspaceItemSummary,
  DspaceStats,
  PagedResult
} from '../../models/openscience.models';
import { ApiPage } from './api.types';
import { SyncRun } from './sync-api.service';

@Injectable({ providedIn: 'root' })
export class DspaceApiService {
  private readonly http = inject(HttpClient);

  stats(query?: DspaceItemQuery): Observable<DspaceStats> {
    return this.http.get<DspaceStats>(`${environment.apiBaseUrl}/dspace/stats`, {
      params: query ? itemFilterParams(query) : undefined
    });
  }

  byResearchOutput(id: number): Observable<DspaceItemSummary[]> {
    return this.http.get<DspaceItemSummary[]>(`${environment.apiBaseUrl}/research-output/${id}/dspace`);
  }

  items(query: DspaceItemQuery): Observable<PagedResult<DspaceItemList>> {
    return this.http
      .get<ApiPage<DspaceItemList>>(`${environment.apiBaseUrl}/dspace/items`, {
        params: itemParams(query)
      })
      .pipe(map(toPage(query.pageSize)));
  }

  matches(query: DspaceItemQuery): Observable<PagedResult<DspaceItemList>> {
    return this.http
      .get<ApiPage<DspaceItemList>>(`${environment.apiBaseUrl}/dspace/matches`, {
        params: itemParams(query)
      })
      .pipe(map(toPage(query.pageSize)));
  }

  detail(uuid: string): Observable<DspaceItemDetail> {
    return this.http.get<DspaceItemDetail>(`${environment.apiBaseUrl}/dspace/items/${uuid}`);
  }

  export(query: DspaceItemQuery): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}/dspace/export.xlsx`, {
      params: itemParams(query),
      headers: new HttpHeaders({ Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
      responseType: 'blob',
      observe: 'response'
    });
  }

  downloadBitstream(itemUuid: string, bitstreamUuid: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}/dspace/items/${itemUuid}/bitstreams/${bitstreamUuid}/download`, {
      responseType: 'blob',
      observe: 'response'
    });
  }

  collections(query: DspaceItemQuery): Observable<PagedResult<DspaceCollectionList>> {
    return this.http
      .get<ApiPage<DspaceCollectionList>>(`${environment.apiBaseUrl}/dspace/collections`, {
        params: catalogParams(query)
      })
      .pipe(map(toPage(query.pageSize)));
  }

  communities(query: DspaceItemQuery): Observable<PagedResult<DspaceCommunityList>> {
    return this.http
      .get<ApiPage<DspaceCommunityList>>(`${environment.apiBaseUrl}/dspace/communities`, {
        params: catalogParams(query)
      })
      .pipe(map(toPage(query.pageSize)));
  }

  errors(query: { stage?: string; page?: number; pageSize?: number }): Observable<PagedResult<DspaceIncident>> {
    return this.http
      .get<ApiPage<DspaceIncident>>(`${environment.apiBaseUrl}/dspace/errors`, {
        params: incidentParams(query)
      })
      .pipe(map(toPage(query.pageSize)));
  }

  history(): Observable<SyncRun[]> {
    return this.http.get<SyncRun[]>(`${environment.apiBaseUrl}/dspace/history`);
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

export function itemFilterParams(query: DspaceItemQuery): HttpParams {
  return itemParams({
    ...query,
    sort: undefined,
    direction: undefined,
    page: undefined,
    pageSize: undefined
  });
}

function itemParams(query: DspaceItemQuery): HttpParams {
  let params = new HttpParams();
  const set = (key: string, value: string | number | boolean | undefined | null) => {
    if (value === undefined || value === null || value === '') {
      return;
    }
    params = params.set(key, String(value));
  };
  set('search', query.search);
  set('outcome', query.outcome);
  set('accessStatus', query.accessStatus);
  set('matchStatus', query.matchStatus);
  set('matchMethod', query.matchMethod);
  set('collectionUuid', query.collectionUuid);
  set('communityUuid', query.communityUuid);
  set('doi', query.doi);
  set('sharedDoi', query.sharedDoi);
  set('hasFile', query.hasFile);
  set('accessComparison', query.accessComparison);
  for (const uuid of query.uuids ?? []) {
    if (uuid) {
      params = params.append('uuid', uuid);
    }
  }
  set('sort', query.sort);
  set('direction', query.direction);
  set('page', query.page);
  set('size', query.pageSize);
  return params;
}

function catalogParams(query: DspaceItemQuery): HttpParams {
  let params = new HttpParams();
  const set = (key: string, value: string | number | undefined | null) => {
    if (value === undefined || value === null || value === '') {
      return;
    }
    params = params.set(key, String(value));
  };
  set('search', query.search);
  set('sort', query.sort);
  set('direction', query.direction);
  set('page', query.page);
  set('size', query.pageSize);
  return params;
}

function incidentParams(query: { stage?: string; page?: number; pageSize?: number }): HttpParams {
  let params = new HttpParams();
  if (query.stage) {
    params = params.set('stage', query.stage);
  }
  if (query.page != null) {
    params = params.set('page', String(query.page));
  }
  if (query.pageSize != null) {
    params = params.set('size', String(query.pageSize));
  }
  return params;
}
