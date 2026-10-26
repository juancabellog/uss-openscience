import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AuditChange, AuditEvent, AuditQuery, AuditSyncError, PagedResult } from '../../models/openscience.models';
import { AuditService } from '../platform-services';
import { ApiPage } from './api.types';

@Injectable()
export class ApiAuditService extends AuditService {
  private readonly http = inject(HttpClient);

  override list(query: AuditQuery): Observable<PagedResult<AuditEvent>> {
    return this.http
      .get<ApiPage<AuditEvent>>(`${environment.apiBaseUrl}/audit/events`, { params: listParams(query) })
      .pipe(map(toPage(query.pageSize)));
  }

  override getById(id: string): Observable<AuditEvent> {
    return this.http.get<AuditEvent>(`${environment.apiBaseUrl}/audit/events/${encodeURIComponent(id)}`);
  }

  override changes(id: string, page = 1, pageSize = 25): Observable<PagedResult<AuditChange>> {
    return this.http
      .get<ApiPage<AuditChange>>(`${environment.apiBaseUrl}/audit/events/${encodeURIComponent(id)}/changes`, {
        params: pageParams(page, pageSize)
      })
      .pipe(map(toPage(pageSize)));
  }

  override errors(id: string, page = 1, pageSize = 25): Observable<PagedResult<AuditSyncError>> {
    return this.http
      .get<ApiPage<AuditSyncError>>(`${environment.apiBaseUrl}/audit/events/${encodeURIComponent(id)}/errors`, {
        params: pageParams(page, pageSize)
      })
      .pipe(map(toPage(pageSize)));
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

function listParams(query: AuditQuery): HttpParams {
  let params = new HttpParams();
  const set = (key: string, value: string | number | undefined | null) => {
    if (value === undefined || value === null || value === '') {
      return;
    }
    params = params.set(key, String(value));
  };
  set('search', query.search);
  set('from', query.from);
  set('to', query.to);
  set('eventType', query.eventType);
  set('source', query.source);
  set('entity', query.entity);
  set('status', query.status);
  set('syncMode', query.syncMode);
  set('actor', query.actor);
  set('sort', query.sort ?? 'startedAt');
  set('direction', query.direction ?? 'desc');
  set('page', query.page ?? 1);
  set('size', query.pageSize ?? 25);
  return params;
}

function pageParams(page: number, pageSize: number): HttpParams {
  return new HttpParams().set('page', String(page)).set('size', String(pageSize));
}
