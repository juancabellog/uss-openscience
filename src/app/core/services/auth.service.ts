import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, firstValueFrom, of } from 'rxjs';
import { isPublicPagePath } from '../auth/auth-public.util';
import { AppModuleCode, AppPermissionCode, CurrentUser } from '../models/openscience.models';
import { AuthApiService } from './api/auth-api.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly currentUser = signal<CurrentUser | null>(null);
  private readonly ready = signal(false);

  readonly user = this.currentUser.asReadonly();
  readonly initialized = this.ready.asReadonly();
  readonly displayName = computed(() => this.currentUser()?.displayName ?? '');

  isAuthenticated(): boolean {
    return this.currentUser() != null;
  }

  can(module: AppModuleCode, permission: AppPermissionCode = 'CONSULT'): boolean {
    const user = this.currentUser();
    if (!user) {
      return false;
    }
    return user.effectivePermissions.some(
      (item) =>
        item.module === module &&
        (item.permission === permission || (permission === 'CONSULT' && item.permission === 'ADMINISTER'))
    );
  }

  canConsult(module: AppModuleCode): boolean {
    return this.can(module, 'CONSULT');
  }

  canAdminister(module: AppModuleCode): boolean {
    return this.can(module, 'ADMINISTER');
  }

  canExport(module: AppModuleCode): boolean {
    return this.canConsult(module);
  }

  canCorrectPure(): boolean {
    return this.canAdminister('PEOPLE') || this.canAdminister('RESEARCH_OUTPUTS');
  }

  hasFunctionalAccess(): boolean {
    return this.firstAllowedPath() !== '/unauthorized';
  }

  firstAllowedPath(): string {
    const order: Array<{ module: AppModuleCode; path: string }> = [
      { module: 'DASHBOARD', path: '/dashboard' },
      { module: 'PEOPLE', path: '/people' },
      { module: 'RESEARCH_OUTPUTS', path: '/research-output' },
      { module: 'JOURNALS', path: '/journals' },
      { module: 'PROJECTS', path: '/projects' },
      { module: 'ORGANIZATIONS', path: '/organizations' },
      { module: 'DIRECTORY', path: '/directory' },
      { module: 'DATA_QUALITY', path: '/data-quality' },
      { module: 'INDICATORS', path: '/indicators' },
      { module: 'INTEGRATIONS', path: '/integrations' },
      { module: 'AUDIT', path: '/audit' },
      { module: 'ADMINISTRATION', path: '/admin' }
    ];
    return order.find((item) => this.canConsult(item.module))?.path ?? '/unauthorized';
  }

  async initialize(): Promise<void> {
    try {
      const user = await firstValueFrom(this.api.me().pipe(catchError(() => of(null))));
      this.currentUser.set(user);
    } finally {
      this.ready.set(true);
    }
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible' && this.isAuthenticated()) {
          void this.refreshPermissions();
        }
      });
    }
  }

  async refreshPermissions(): Promise<void> {
    const user = await firstValueFrom(this.api.me().pipe(catchError(() => of(null))));
    this.currentUser.set(user);
  }

  async signIn(email: string, password: string): Promise<CurrentUser> {
    const user = await firstValueFrom(this.api.login(email, password));
    this.currentUser.set(user);
    return user;
  }

  async signOut(): Promise<void> {
    try {
      await firstValueFrom(this.api.logout().pipe(catchError(() => of(null))));
    } finally {
      this.currentUser.set(null);
      await this.router.navigateByUrl('/login');
    }
  }

  acceptUser(user: CurrentUser): void {
    this.currentUser.set(user);
  }

  clear(): void {
    this.currentUser.set(null);
  }

  isPublicPath(url: string): boolean {
    return isPublicPagePath(url);
  }

  safeReturnUrl(value: string | null | undefined): string {
    if (!value || !value.startsWith('/') || value.startsWith('//') || value.includes('://')) {
      return this.firstAllowedPath();
    }
    return value;
  }
}
