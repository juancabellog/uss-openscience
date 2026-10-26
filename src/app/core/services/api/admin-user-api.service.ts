import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AdminUserQuery, AdminUserSummary, PagedResult } from '../../models/openscience.models';
import { ApiPage } from './api.types';

@Injectable({ providedIn: 'root' })
export class AdminUserApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/users`;

  list(query: AdminUserQuery): Observable<PagedResult<AdminUserSummary>> {
    const page = Math.max(query.page ?? 1, 1);
    const size = query.pageSize ?? 25;
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sort', query.sort ?? 'createdAt')
      .set('direction', query.direction ?? 'desc');
    if (query.search) {
      params = params.set('search', query.search);
    }
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.roleId != null) {
      params = params.set('roleId', String(query.roleId));
    }
    return this.http.get<ApiPage<AdminUserSummary>>(this.base, { params, withCredentials: true }).pipe(
      map((result) => ({
        items: result.items,
        total: result.totalElements,
        page: (result.page ?? 0) + 1,
        pageSize: result.size ?? size
      }))
    );
  }

  get(id: number): Observable<AdminUserSummary> {
    return this.http.get<AdminUserSummary>(`${this.base}/${id}`, { withCredentials: true });
  }

  create(body: Partial<AdminUserSummary> & { roleIds: number[] }): Observable<AdminUserSummary> {
    return this.http.post<AdminUserSummary>(this.base, body, { withCredentials: true });
  }

  update(id: number, body: Partial<AdminUserSummary> & { roleIds: number[] }): Observable<AdminUserSummary> {
    return this.http.put<AdminUserSummary>(`${this.base}/${id}`, body, { withCredentials: true });
  }

  disable(id: number): Observable<AdminUserSummary> {
    return this.http.post<AdminUserSummary>(`${this.base}/${id}/disable`, {}, { withCredentials: true });
  }

  enable(id: number): Observable<AdminUserSummary> {
    return this.http.post<AdminUserSummary>(`${this.base}/${id}/enable`, {}, { withCredentials: true });
  }

  resend(id: number): Observable<AdminUserSummary> {
    return this.http.post<AdminUserSummary>(`${this.base}/${id}/resend-activation`, {}, { withCredentials: true });
  }

  delete(id: number): Observable<{ code: string; mode: string; message: string }> {
    return this.http.delete<{ code: string; mode: string; message: string }>(`${this.base}/${id}`, {
      withCredentials: true
    });
  }
}
