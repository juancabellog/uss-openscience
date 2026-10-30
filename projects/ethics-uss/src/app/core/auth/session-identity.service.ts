import { Injectable, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { Role, User } from '../models/ethics.models';
import { demoSelectorVisible } from './demo-mode';

interface SessionRole {
  id?: string;
}

interface SessionProfile {
  id?: string;
  name?: string;
  email?: string;
  roles?: SessionRole[];
}

const ROLE_ORDER: Role[] = ['ADMINISTRATOR', 'COMMITTEE_PRESIDENT', 'EXECUTIVE_SECRETARY', 'REVIEWER', 'RESEARCHER'];

/**
 * Identidad de la cookie de OpenScience. No lee ni escribe el perfil elegido en el navegador.
 */
@Injectable({ providedIn: 'root' })
export class SessionIdentityService {
  readonly user = signal<User | null>(null);
  readonly denied = signal(false);
  readonly ready = signal(demoSelectorVisible(environment.demoEnabled, environment.production));
  private pending: Promise<User | null> | null = null;

  refresh(): Promise<User | null> {
    this.ready.set(false);
    this.pending = null;
    this.denied.set(false);
    return this.ensure();
  }

  clear(): void {
    this.user.set(null);
    this.denied.set(false);
    this.ready.set(true);
    this.pending = Promise.resolve(null);
  }

  ensure(): Promise<User | null> {
    if (demoSelectorVisible(environment.demoEnabled, environment.production)) {
      return Promise.resolve(null);
    }
    if (this.ready()) {
      return Promise.resolve(this.user());
    }
    this.pending ??= this.load();
    return this.pending;
  }

  private async load(): Promise<User | null> {
    try {
      const profileResponse = await this.read(`${environment.futureApiBaseUrl}/me`);
      if (profileResponse.status === 403) {
        this.denied.set(true);
      }
      const profile = profileResponse.body as SessionProfile | null;
      if (!profile) {
        this.user.set(null);
        return null;
      }
      const permissions = (await this.read(`${environment.futureApiBaseUrl}/permissions`)).body as { capabilities?: string[] } | null;
      const roles = this.rolesOf(profile);
      const primary = roles[0] ?? 'RESEARCHER';
      this.user.set({
        id: profile.id ?? '',
        displayName: profile.name ?? profile.email ?? '',
        email: profile.email ?? '',
        role: primary,
        roles: roles.filter((role) => role !== primary),
        committees: [],
        ethicsAccess: 'ACTIVE',
        personKind: 'INSTITUTIONAL',
        boundToSession: true,
        hideRoleLabel: roles.length === 0,
        grantedCapabilities: Array.isArray(permissions?.capabilities) ? permissions.capabilities : []
      });
      return this.user();
    } catch {
      this.user.set(null);
      return null;
    } finally {
      this.ready.set(true);
    }
  }

  private rolesOf(profile: SessionProfile): Role[] {
    const present = new Set((profile.roles ?? []).map((role) => role.id).filter((id): id is Role => ROLE_ORDER.includes(id as Role)));
    return ROLE_ORDER.filter((role) => present.has(role));
  }

  private async read(url: string): Promise<{ status: number; body: unknown }> {
    const response = await fetch(url, { credentials: 'include' });
    if (response.status === 401 || response.status === 403) {
      return { status: response.status, body: null };
    }
    if (!response.ok) {
      throw new Error('ethics session unavailable');
    }
    return { status: response.status, body: await response.json() };
  }
}
