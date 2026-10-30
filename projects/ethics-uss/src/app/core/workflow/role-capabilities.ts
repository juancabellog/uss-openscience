import { YAML_CAPABILITIES } from '../../features/dashboard/home-institutional';
import { ROLE_LABELS } from '../../mocks/catalogs';
import { Role, User } from '../models/ethics.models';

/**
 * Matriz de prototipo. La interfaz solo oculta o deshabilita.
 * AuthorizationService vuelve a autorizar cada acción con permissions-V3.yml.
 * El nombre del rol agrupa capabilities. No decide la acción.
 */
export type Capability = string;

export type PermissionScope = 'GLOBAL' | 'COMMITTEE' | 'CASE' | 'OWN_PROJECT';

export const ROLE_SCOPE: Record<Role, PermissionScope> = {
  ADMINISTRATOR: 'GLOBAL',
  COMMITTEE_PRESIDENT: 'COMMITTEE',
  EXECUTIVE_SECRETARY: 'COMMITTEE',
  REVIEWER: 'CASE',
  RESEARCHER: 'OWN_PROJECT'
};

export const ROLE_CAPABILITIES: Record<Role, readonly Capability[]> = {
  ADMINISTRATOR: YAML_CAPABILITIES.ADMINISTRATOR,
  COMMITTEE_PRESIDENT: YAML_CAPABILITIES.COMMITTEE_PRESIDENT,
  EXECUTIVE_SECRETARY: YAML_CAPABILITIES.EXECUTIVE_SECRETARY,
  REVIEWER: YAML_CAPABILITIES.REVIEWER,
  RESEARCHER: YAML_CAPABILITIES.RESEARCHER
};

export function rolesOf(user: Pick<User, 'role' | 'roles'> | null | undefined): Role[] {
  if (!user) {
    return [];
  }
  return [...new Set([user.role, ...(user.roles ?? [])])];
}

export function hasScope(user: Pick<User, 'role' | 'roles'> | null | undefined, scope: PermissionScope): boolean {
  return rolesOf(user).some((role) => ROLE_SCOPE[role] === scope);
}

export function holds(user: Pick<User, 'role' | 'roles'> | null | undefined, capability: string): boolean {
  return rolesOf(user).some((role) => ROLE_CAPABILITIES[role].includes(capability));
}

/** Etiqueta del rol principal. "Externo/a" es metadata de la persona, no un rol. */
export function profileRoleKey(user: Pick<User, 'role' | 'externalReviewer'>): string {
  if (user.externalReviewer) {
    return 'reviewers.externalRole';
  }
  return ROLE_LABELS[user.role] ?? user.role;
}
