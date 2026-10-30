import { Injectable, inject } from '@angular/core';
import { isAllowedInstitutionalEmail, institutionalDomain } from '../identity/institutional-email';
import { Role, User } from '../models/ethics.models';
import { DemoAuthService } from '../auth/demo-auth.service';
import { AuditService } from './audit.service';
import { EthicsStore } from './ethics-store.service';
import { uid } from './ids';
import { PermissionService } from './permission.service';

const STRUCTURAL: readonly Role[] = ['RESEARCHER', 'ADMINISTRATOR', 'COMMITTEE_PRESIDENT', 'EXECUTIVE_SECRETARY'];

export type UserAdminCode =
  | 'denied'
  | 'domain'
  | 'institutional-flow'
  | 'invalid-email'
  | 'external-role'
  | 'pure'
  | 'confirm'
  | 'not-found';

export interface UserAdminFailure {
  ok: false;
  code: UserAdminCode;
}

export interface UserAdminSuccess {
  ok: true;
  userId: string;
  notice: 'created' | 'enabled' | 'exists' | 'already-ethics' | 'updated' | 'disabled' | 'role';
}

export type UserAdminResult = UserAdminSuccess | UserAdminFailure;

export function personKindOf(user: Pick<User, 'personKind' | 'externalReviewer'>): 'INSTITUTIONAL' | 'EXTERNAL' {
  if (user.personKind) {
    return user.personKind;
  }
  return user.externalReviewer ? 'EXTERNAL' : 'INSTITUTIONAL';
}

export function ethicsOpen(user: Pick<User, 'ethicsAccess' | 'personKind' | 'externalReviewer'>): boolean {
  if (personKindOf(user) === 'EXTERNAL') {
    return user.ethicsAccess === 'ACTIVE';
  }
  return user.ethicsAccess !== 'INACTIVE';
}

@Injectable({ providedIn: 'root' })
export class UserAdminService {
  private readonly store = inject(EthicsStore);
  private readonly auth = inject(DemoAuthService);
  private readonly audit = inject(AuditService);
  private readonly permissions = inject(PermissionService);

  findByEmail(email: string): User | undefined {
    const normalized = email.trim().toLowerCase();
    return this.store.snapshot().users.find((user) => user.email.trim().toLowerCase() === normalized);
  }

  selfRegister(email: string, givenName = '', familyName = ''): UserAdminResult {
    if (!isAllowedInstitutionalEmail(email)) {
      return { ok: false, code: 'domain' };
    }
    return this.activateInstitutional(email, givenName, familyName, false);
  }

  enableInstitutional(email: string, givenName = '', familyName = ''): UserAdminResult {
    if (!this.canManage()) {
      return { ok: false, code: 'denied' };
    }
    if (!isAllowedInstitutionalEmail(email)) {
      return { ok: false, code: 'domain' };
    }
    return this.activateInstitutional(email, givenName, familyName, true);
  }

  createExternal(input: {
    givenName: string;
    familyName: string;
    email: string;
    institution: string;
    positionTitle?: string;
    specialty?: string;
  }): UserAdminResult {
    if (!this.canManage()) {
      return { ok: false, code: 'denied' };
    }
    if (!institutionalDomain(input.email)) {
      return { ok: false, code: 'invalid-email' };
    }
    if (isAllowedInstitutionalEmail(input.email)) {
      return { ok: false, code: 'institutional-flow' };
    }
    const existing = this.findByEmail(input.email);
    if (existing) {
      if (personKindOf(existing) === 'INSTITUTIONAL') {
        return { ok: false, code: 'institutional-flow' };
      }
      if (ethicsOpen(existing)) {
        return { ok: true, userId: existing.id, notice: 'already-ethics' };
      }
      this.patch(existing.id, { ethicsAccess: 'ACTIVE' });
      this.audit.record({
        action: 'ENABLE_ETHICS',
        objectType: 'UserAccount',
        objectId: existing.id,
        description: 'Se ha habilitado su acceso a la Plataforma de Ética de Proyectos.',
        newValue: existing.email
      });
      return { ok: true, userId: existing.id, notice: 'enabled' };
    }
    const id = uid('u');
    const user: User = {
      id,
      displayName: `${input.givenName.trim()} ${input.familyName.trim()}`.trim(),
      email: input.email.trim(),
      role: 'REVIEWER',
      committees: [],
      externalReviewer: true,
      personKind: 'EXTERNAL',
      ethicsAccess: 'ACTIVE',
      institution: input.institution.trim(),
      positionTitle: input.positionTitle?.trim() || undefined,
      specialty: input.specialty?.trim() || undefined
    };
    this.store.update((state) => ({ ...state, users: [...state.users, user] }));
    this.audit.record({
      action: 'CREATE_EXTERNAL_PERSON',
      objectType: 'Person',
      objectId: id,
      description: 'Se ha habilitado su acceso a la Plataforma de Ética de Proyectos.',
      newValue: user.email
    });
    return { ok: true, userId: id, notice: 'created' };
  }

  updateExternal(userId: string, patch: { displayName?: string; email?: string; institution?: string; positionTitle?: string; specialty?: string }): UserAdminResult {
    if (!this.canManage()) {
      return { ok: false, code: 'denied' };
    }
    const current = this.store.snapshot().users.find((user) => user.id === userId);
    if (!current) {
      return { ok: false, code: 'not-found' };
    }
    if (personKindOf(current) !== 'EXTERNAL') {
      if (current.purePersonId != null) {
        return { ok: false, code: 'pure' };
      }
      return { ok: false, code: 'external-role' };
    }
    if (patch.email && isAllowedInstitutionalEmail(patch.email)) {
      return { ok: false, code: 'institutional-flow' };
    }
    const next: User = {
      ...current,
      displayName: patch.displayName?.trim() || current.displayName,
      email: patch.email?.trim() || current.email,
      institution: patch.institution?.trim() || current.institution,
      positionTitle: patch.positionTitle?.trim() || current.positionTitle,
      specialty: patch.specialty?.trim() || current.specialty
    };
    this.patch(userId, next);
    this.audit.record({
      action: 'UPDATE_EXTERNAL_PERSON',
      objectType: 'Person',
      objectId: userId,
      description: 'Actualizó datos locales del revisor externo.',
      oldValue: [current.displayName, current.email, current.institution].filter(Boolean).join(' · '),
      newValue: [next.displayName, next.email, next.institution].filter(Boolean).join(' · ')
    });
    return { ok: true, userId, notice: 'updated' };
  }

  grantStructuralRole(userId: string, role: Role): UserAdminResult {
    if (!this.canManage()) {
      return { ok: false, code: 'denied' };
    }
    if (!STRUCTURAL.includes(role)) {
      return { ok: false, code: 'external-role' };
    }
    const current = this.store.snapshot().users.find((user) => user.id === userId);
    if (!current || personKindOf(current) !== 'INSTITUTIONAL') {
      return { ok: false, code: 'external-role' };
    }
    const roles = [...new Set([current.role, ...(current.roles ?? []), role])];
    this.patch(userId, { roles: roles.filter((item) => item !== current.role) });
    this.audit.record({
      action: 'GRANT_ETHICS_ROLE',
      objectType: 'RoleAssignment',
      objectId: userId,
      description: 'Asignó una función institucional.',
      newValue: role
    });
    return { ok: true, userId, notice: 'role' };
  }

  disableEthics(userId: string, confirmed: boolean): UserAdminResult {
    if (!this.canManage()) {
      return { ok: false, code: 'denied' };
    }
    const current = this.store.snapshot().users.find((user) => user.id === userId);
    if (!current) {
      return { ok: false, code: 'not-found' };
    }
    const active = this.store.snapshot().assignments.some((item) =>
      item.reviewerId === userId && !item.supersededAt && (item.status === 'PENDING' || item.status === 'ACCEPTED')
    );
    if (active && !confirmed) {
      return { ok: false, code: 'confirm' };
    }
    this.patch(userId, { ethicsAccess: 'INACTIVE' });
    this.audit.record({
      action: 'DISABLE_ETHICS',
      objectType: 'UserApplication',
      objectId: userId,
      description: 'Revocó el acceso a Ethics. La cuenta y el historial se conservan.',
      oldValue: 'ACTIVE',
      newValue: 'INACTIVE'
    });
    return { ok: true, userId, notice: 'disabled' };
  }

  private activateInstitutional(email: string, givenName: string, familyName: string, allowInactive: boolean): UserAdminResult {
    const existing = this.findByEmail(email);
    if (existing) {
      if (personKindOf(existing) === 'EXTERNAL') {
        return { ok: false, code: 'external-role' };
      }
      if (ethicsOpen(existing)) {
        return { ok: true, userId: existing.id, notice: 'already-ethics' };
      }
      if (!allowInactive) {
        return { ok: false, code: 'denied' };
      }
      const roles = new Set([existing.role, ...(existing.roles ?? [])]);
      const extra = roles.has('RESEARCHER') ? existing.roles : [...(existing.roles ?? []), 'RESEARCHER' as Role];
      this.patch(existing.id, { ethicsAccess: 'ACTIVE', personKind: 'INSTITUTIONAL', roles: extra?.filter((role) => role !== existing.role) });
      this.audit.record({
        action: 'ENABLE_ETHICS',
        objectType: 'UserApplication',
        objectId: existing.id,
        description: 'Se ha habilitado su acceso a la Plataforma de Ética de Proyectos. Puede utilizar las mismas credenciales de OpenScience.',
        newValue: existing.email
      });
      return { ok: true, userId: existing.id, notice: 'enabled' };
    }
    if (!givenName.trim() || !familyName.trim()) {
      return { ok: false, code: 'not-found' };
    }
    const id = uid('u');
    const user: User = {
      id,
      displayName: `${givenName.trim()} ${familyName.trim()}`.trim() || email.trim(),
      email: email.trim(),
      role: 'RESEARCHER',
      committees: [],
      personKind: 'INSTITUTIONAL',
      ethicsAccess: 'ACTIVE'
    };
    this.store.update((state) => ({ ...state, users: [...state.users, user] }));
    this.audit.record({
      action: 'ENABLE_ETHICS',
      objectType: 'UserAccount',
      objectId: id,
      description: 'Se ha habilitado su acceso a la Plataforma de Ética de Proyectos. Puede utilizar las mismas credenciales de OpenScience.',
      newValue: user.email
    });
    return { ok: true, userId: id, notice: 'created' };
  }

  private canManage(): boolean {
    return this.permissions.can(this.auth.currentUser(), 'MANAGE_USERS');
  }

  private patch(userId: string, patch: Partial<User>): void {
    this.store.update((state) => ({
      ...state,
      users: state.users.map((user) => (user.id === userId ? { ...user, ...patch } : user))
    }));
  }
}
