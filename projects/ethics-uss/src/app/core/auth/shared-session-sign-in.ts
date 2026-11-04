import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { isAllowedInstitutionalEmail } from '../identity/institutional-email';
import { LoginFailure, classifyLoginResponse } from './ethics-entry';
import { ethicsStartOutcome, InstitutionalStart } from './ethics-start-outcome';

export type { InstitutionalStart } from './ethics-start-outcome';
export { ethicsStartOutcome } from './ethics-start-outcome';

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

  async startInstitutional(email: string, locale?: string): Promise<InstitutionalStart> {
    try {
      const response = await fetch(`${this.base}/ethics-start`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, locale })
      });
      let code: string | null = null;
      let emailSent = false;
      try {
        const body = await response.json() as { code?: unknown; emailSent?: unknown };
        code = typeof body.code === 'string' ? body.code : null;
        emailSent = body.emailSent === true;
      } catch {
        code = null;
      }
      return ethicsStartOutcome(response.ok, code, emailSent);
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

  async previewActivation(token: string): Promise<ActivationPreview | null> {
    try {
      const response = await fetch(`${this.base}/activate?token=${encodeURIComponent(token)}`, {
        credentials: 'include'
      });
      if (!response.ok) {
        return null;
      }
      return await response.json() as ActivationPreview;
    } catch {
      return null;
    }
  }

  async submitActivation(token: string, password: string, profile?: ActivationProfile): Promise<string | null> {
    try {
      const response = await fetch(`${this.base}/activate`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token, password, ...profile })
      });
      if (response.ok) {
        return null;
      }
      return await readCode(response) ?? 'unavailable';
    } catch {
      return 'unavailable';
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

export interface ActivationPreview {
  valid: boolean;
  status?: string;
  maskedEmail?: string | null;
  profileRequired?: boolean;
  ethicsInvitation?: boolean;
  pureReadonly?: boolean;
  givenName?: string | null;
  familyName?: string | null;
  positionTitle?: string | null;
  preferredLanguage?: string | null;
}

export interface ActivationProfile {
  givenName: string;
  familyName: string;
  campus: string;
  faculty: string;
  positionTitle: string;
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
