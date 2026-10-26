import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AdminRole, AuthPermission } from '../../models/openscience.models';

@Injectable({ providedIn: 'root' })
export class AdminRoleApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/roles`;

  list(): Observable<AdminRole[]> {
    return this.http.get<AdminRole[]>(this.base, { withCredentials: true });
  }

  get(id: number): Observable<AdminRole> {
    return this.http.get<AdminRole>(`${this.base}/${id}`, { withCredentials: true });
  }

  create(body: {
    code: string;
    name: string;
    description?: string;
    active?: boolean;
    permissions: AuthPermission[];
  }): Observable<AdminRole> {
    return this.http.post<AdminRole>(this.base, body, { withCredentials: true });
  }

  update(
    id: number,
    body: { name: string; description?: string; active?: boolean; permissions: AuthPermission[] }
  ): Observable<AdminRole> {
    return this.http.put<AdminRole>(`${this.base}/${id}`, body, { withCredentials: true });
  }

  duplicate(id: number): Observable<AdminRole> {
    return this.http.post<AdminRole>(`${this.base}/${id}/duplicate`, {}, { withCredentials: true });
  }

  disable(id: number): Observable<AdminRole> {
    return this.http.post<AdminRole>(`${this.base}/${id}/disable`, {}, { withCredentials: true });
  }
}
