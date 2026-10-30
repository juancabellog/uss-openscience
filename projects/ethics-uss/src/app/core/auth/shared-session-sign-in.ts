import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { isAllowedInstitutionalEmail } from '../identity/institutional-email';
import { LoginFailure, classifyLoginResponse } from './ethics-entry';

/**
 * Llama a /api/v1/auth. La cookie OS_SESSION la escribe el backend.
 * No guarda contraseña ni abre otra sesión de Ethics.
 */
@Injectable({ providedIn: 'root' })
export class SharedSessionSignIn {
  private readonly base = `${environment.apiBaseUrl}/auth`;

  async signIn(email: string, password: string): Promise<LoginFailure | null> {
    try {
      const response = await fetch(`${this.base}/login`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
      });
      return classifyLoginResponse(response.status, await readCode(response));
    } catch {
      return 'unavailable';
    }
  }

  async requestPasswordReset(email: string, locale?: string): Promise<void> {
    try {
      await fetch(`${this.base}/forgot-password`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, locale })
      });
    } catch {
      // El backend tampoco revela si el correo existe.
    }
  }

  async startInstitutional(email: string, locale?: string): Promise<'domain' | 'login' | 'accepted' | 'rejected'> {
    if (!isAllowedInstitutionalEmail(email)) {
      return 'domain';
    }
    try {
      const response = await fetch(`${this.base}/ethics-start`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, locale })
      });
      let code: string | null = null;
      try {
        const body = await response.json() as { code?: unknown };
        code = typeof body.code === 'string' ? body.code : null;
      } catch {
        code = null;
      }
      if (code === 'ETHICS_USE_LOGIN') {
        return 'login';
      }
      if (code === 'NOT_INSTITUTIONAL_DOMAIN') {
        return 'domain';
      }
      return response.ok ? 'accepted' : 'rejected';
    } catch {
      return 'rejected';
    }
  }

  async registerInstitutional(firstName: string, lastName: string, email: string): Promise<'domain' | 'accepted' | 'rejected'> {
    if (!isAllowedInstitutionalEmail(email)) {
      return 'domain';
    }
    try {
      const response = await fetch(`${this.base}/register`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ firstName, lastName, email })
      });
      return response.ok ? 'accepted' : 'rejected';
    } catch {
      return 'rejected';
    }
  }

  async signOut(): Promise<void> {
    try {
      await fetch(`${this.base}/logout`, { method: 'POST', credentials: 'include' });
    } catch {
      // La pantalla vuelve al login aunque el servidor no responda.
    }
  }
}

async function readCode(response: Response): Promise<string | null> {
  if (response.ok) {
    return null;
  }
  try {
    const body = await response.json() as { code?: unknown };
    return typeof body.code === 'string' ? body.code : null;
  } catch {
    return null;
  }
}
