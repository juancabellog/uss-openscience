import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { DemoAuthService } from '../auth/demo-auth.service';
import { ReviewerAssignment, User } from '../models/ethics.models';
import { EthicsStore } from '../services/ethics-store.service';
import { rolesOf } from '../workflow/role-capabilities';
import {
  EthicsWorkspace,
  FunctionView,
  WORKSPACE_STORAGE_KEY,
  buildWorkspaces,
  chooseWorkspace
} from './workspace';

/**
 * Preferencia de presentación. La cookie, los roles y las capabilities no se leen de aquí.
 */
@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly auth = inject(DemoAuthService);
  private readonly store = inject(EthicsStore);
  private readonly router = inject(Router);
  private readonly remote = signal<FunctionView[] | null>(null);
  private readonly stored = signal<string | null>(readStoredWorkspace());
  private readonly confirmed = signal(false);
  private requestedFor: string | null = null;

  readonly options = computed(() => {
    const user = this.auth.currentUser();
    if (!user) {
      return [];
    }
    const assignments = this.store.state().assignments;
    const remote = this.remote();
    const local = localViews(user, assignments);
    const views = remote ? [...remote, ...local.filter((item) => item.roleId === 'REVIEWER')] : local;
    const capabilities = user.grantedCapabilities ?? [];
    return buildWorkspaces(views, capabilities.includes('VIEW_OWN_REVIEW'));
  });

  readonly active = computed(() => chooseWorkspace(this.options(), this.stored()));
  readonly showSelector = computed(() => this.options().length > 1);

  constructor() {
    effect(() => {
      const user = this.auth.currentUser();
      if (!user?.boundToSession || !user.id) {
        this.remote.set(null);
        this.requestedFor = null;
        this.confirmed.set(true);
        return;
      }
      if (this.requestedFor === user.id) {
        return;
      }
      this.confirmed.set(false);
      this.requestedFor = user.id;
      void this.load(user.id);
    });
    effect(() => {
      const items = this.options();
      const stored = this.stored();
      if (!this.confirmed() || !items.length) {
        return;
      }
      if (stored && items.some((item) => item.id === stored)) {
        return;
      }
      const next = chooseWorkspace(items, null);
      if (next) {
        this.stored.set(next.id);
        writeStoredWorkspace(next.id);
      }
    });
  }

  select(id: string): void {
    if (!this.options().some((item) => item.id === id) || this.stored() === id) {
      return;
    }
    this.stored.set(id);
    writeStoredWorkspace(id);
    void this.router.navigateByUrl('/');
  }

  private async load(userId: string): Promise<void> {
    try {
      const response = await fetch(`${environment.futureApiBaseUrl}/me/functions`, { credentials: 'include' });
      if (!response.ok || this.auth.currentUser()?.id !== userId) {
        return;
      }
      const body = await response.json() as FunctionView[];
      this.remote.set(Array.isArray(body) ? body : []);
      this.confirmed.set(true);
    } catch {
      this.remote.set(null);
      this.confirmed.set(true);
    }
  }
}

function localViews(user: User, assignments: readonly ReviewerAssignment[]): FunctionView[] {
  const views: FunctionView[] = [];
  for (const role of rolesOf(user)) {
    if (role === 'REVIEWER') {
      views.push({ roleId: 'REVIEWER', status: 'ACTIVE' });
      continue;
    }
    if ((role === 'COMMITTEE_PRESIDENT' || role === 'EXECUTIVE_SECRETARY') && user.committees.length) {
      for (const scopeId of user.committees) {
        views.push({ roleId: role, scopeType: 'COMMITTEE', scopeId, status: 'ACTIVE' });
      }
      continue;
    }
    views.push({ roleId: role, status: 'ACTIVE' });
  }
  const own = assignments.filter((item) => item.reviewerId === user.id && !item.supersededAt);
  if (own.length) {
    const status = own.some((item) => item.status !== 'COMPLETED') ? 'ACTIVE' : 'COMPLETED';
    views.push({ roleId: 'REVIEWER', status });
  }
  return views;
}

function readStoredWorkspace(): string | null {
  if (typeof localStorage === 'undefined') {
    return null;
  }
  const value = localStorage.getItem(WORKSPACE_STORAGE_KEY);
  return value && value.trim() ? value : null;
}

function writeStoredWorkspace(id: string): void {
  if (typeof localStorage === 'undefined') {
    return;
  }
  localStorage.setItem(WORKSPACE_STORAGE_KEY, id);
}
