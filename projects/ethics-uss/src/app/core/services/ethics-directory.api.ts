import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface EthicsDirectoryRole {
  id: string;
  assignmentId?: string | null;
  idLabel: string;
  contextual: boolean;
  removable?: boolean;
  scopeType?: string | null;
  scopeId?: string | null;
  scopeLabel?: string | null;
}

export interface EthicsDirectoryUser {
  id: string;
  givenName: string;
  familyName: string;
  displayName: string;
  email: string;
  personKind: 'INSTITUTIONAL' | 'EXTERNAL';
  dataSource: 'PURE' | 'LOCAL';
  purePersonId: number | null;
  accountStatus: string;
  ethicsAccess: boolean;
  roles: EthicsDirectoryRole[];
}

export interface EthicsDirectoryPage {
  items: EthicsDirectoryUser[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface EthicsOnboarding {
  kind: 'EXISTING_USER' | 'PURE_PERSON' | 'NEW_INSTITUTIONAL_PERSON';
  email: string;
  givenName?: string | null;
  familyName?: string | null;
  position?: string | null;
  ethicsAccess?: boolean;
  accountStatus?: string | null;
  invitation?: 'PENDING' | 'ACCEPTED' | 'EXPIRED' | 'CANCELLED' | null;
  emailSent?: boolean;
  user?: EthicsDirectoryUser | null;
}

export interface EthicsDirectoryQuery {
  search: string;
  type: string;
  status: string;
  ethicsAccess: string;
  page: number;
  size: number;
}

export class EthicsDirectoryError extends Error {
  constructor(readonly status: number, readonly code: string) {
    super(code);
  }
}

@Injectable({ providedIn: 'root' })
export class EthicsDirectoryApi {
  private readonly base = `${environment.futureApiBaseUrl}/users`;

  list(query: EthicsDirectoryQuery): Promise<EthicsDirectoryPage> {
    const params = new URLSearchParams();
    if (query.search.trim()) {
      params.set('search', query.search.trim());
    }
    params.set('type', query.type);
    params.set('status', query.status);
    params.set('ethicsAccess', query.ethicsAccess);
    params.set('page', String(query.page));
    params.set('size', String(query.size));
    return this.request<EthicsDirectoryPage>(`${this.base}?${params.toString()}`);
  }

  resolveInstitutional(email: string): Promise<EthicsOnboarding> {
    return this.request<EthicsOnboarding>(`${this.base}/resolve`, {
      method: 'POST',
      body: JSON.stringify({ email })
    });
  }

  inviteInstitutional(email: string): Promise<EthicsOnboarding> {
    return this.request<EthicsOnboarding>(`${this.base}/invite`, {
      method: 'POST',
      body: JSON.stringify({ email })
    });
  }

  get(id: string): Promise<EthicsDirectoryUser> {
    return this.request<EthicsDirectoryUser>(`${this.base}/${encodeURIComponent(id)}`);
  }

  setAccess(id: string, active: boolean, confirmed: boolean): Promise<EthicsDirectoryUser> {
    return this.request<EthicsDirectoryUser>(`${this.base}/${encodeURIComponent(id)}/ethics-access`, {
      method: 'POST',
      body: JSON.stringify({ active, confirmed })
    });
  }

  grantRole(id: string, roleId: string): Promise<EthicsDirectoryUser> {
    return this.request<EthicsDirectoryUser>(`${this.base}/${encodeURIComponent(id)}/roles`, {
      method: 'POST',
      body: JSON.stringify({ roleId })
    });
  }

  revokeRole(id: string, assignmentId: string): Promise<EthicsDirectoryUser> {
    return this.request<EthicsDirectoryUser>(
      `${this.base}/${encodeURIComponent(id)}/roles/${encodeURIComponent(assignmentId)}`,
      { method: 'DELETE' }
    );
  }

  updateLocal(id: string, body: { givenName: string; familyName: string }): Promise<EthicsDirectoryUser> {
    return this.request<EthicsDirectoryUser>(`${this.base}/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(body)
    });
  }

  private async request<T>(url: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(url, {
      ...init,
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers
      }
    });
    if (!response.ok) {
      let code = 'LOAD_FAILED';
      try {
        const body = await response.json() as { code?: string };
        if (body.code) {
          code = body.code;
        }
      } catch {
        code = 'LOAD_FAILED';
      }
      throw new EthicsDirectoryError(response.status, code);
    }
    return response.json() as Promise<T>;
  }
}
