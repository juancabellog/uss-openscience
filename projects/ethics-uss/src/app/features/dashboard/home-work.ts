import { CommitteeCase, CommitteeCaseStatus, User } from '../../core/models/ethics.models';
import { Capability, ROLE_CAPABILITIES, ROLE_SCOPE, rolesOf } from '../../core/workflow/role-capabilities';

/**
 * Catálogo del Inicio operativo. Cada indicador y cada motivo de atención
 * dependen de una capability ya existente, no del nombre del rol.
 * Otros perfiles se agregan aquí cuando su bandeja esté definida.
 */
const WORK_HOME_CAPABILITIES: readonly Capability[] = [
  'ASSIGN_REVIEWERS',
  'PREPARE_RESOLUTION',
  'VALIDATE_RESOLUTION',
  'CONSOLIDATE_OBSERVATIONS',
  'VIEW_ALL_REVIEWS',
  'PUBLISH_OBSERVATIONS'
];

export interface HomeIndicatorDefinition {
  id: string;
  labelKey: string;
  capabilities: readonly Capability[];
  count: (items: CommitteeCase[], expiring: (item: CommitteeCase) => boolean) => number;
}

export const HOME_INDICATORS: readonly HomeIndicatorDefinition[] = [
  {
    id: 'admissibility',
    labelKey: 'dashboard.pendingAdmissibility',
    capabilities: ['ASSIGN_REVIEWERS'],
    count: (items) => items.filter((item) => item.status === 'ADMISSIBILITY').length
  },
  {
    id: 'reviews',
    labelKey: 'dashboard.reviewsInProgress',
    capabilities: ['VIEW_ALL_REVIEWS'],
    count: (items) => items.filter((item) => item.status === 'IN_REVIEW').length
  },
  {
    id: 'resolution',
    labelKey: 'dashboard.pendingResolution',
    capabilities: ['PREPARE_RESOLUTION', 'VALIDATE_RESOLUTION'],
    count: (items) => items.filter((item) => item.status === 'PENDING_RESOLUTION' || item.status === 'PENDING_PRESIDENT_VALIDATION').length
  },
  {
    id: 'expiring',
    labelKey: 'dashboard.expiring',
    capabilities: ['VIEW_ALL_REVIEWS'],
    count: (items, expiring) => items.filter((item) => expiring(item)).length
  }
];

export interface AttentionRule {
  status: CommitteeCaseStatus;
  capabilities: readonly Capability[];
  reasonKey: string;
  priority: number;
}

/** La primera regla cuyo estado y capability coinciden explica la fila. */
export const ATTENTION_RULES: readonly AttentionRule[] = [
  { status: 'ADMISSIBILITY', capabilities: ['ASSIGN_REVIEWERS'], reasonKey: 'dashboard.reviewAdmissibility', priority: 10 },
  { status: 'PENDING_ASSIGNMENT', capabilities: ['ASSIGN_REVIEWERS'], reasonKey: 'dashboard.assignReviewers', priority: 20 },
  { status: 'PENDING_ACCEPTANCE', capabilities: ['ASSIGN_REVIEWERS'], reasonKey: 'dashboard.awaitingAcceptance', priority: 30 },
  { status: 'WITH_OBSERVATIONS', capabilities: ['CONSOLIDATE_OBSERVATIONS'], reasonKey: 'dashboard.consolidate', priority: 40 },
  { status: 'PENDING_RESOLUTION', capabilities: ['PREPARE_RESOLUTION'], reasonKey: 'dashboard.prepareResolution', priority: 50 },
  { status: 'PENDING_PRESIDENT_VALIDATION', capabilities: ['VALIDATE_RESOLUTION'], reasonKey: 'dashboard.validateResolution', priority: 60 },
  { status: 'PENDING_PRESIDENT_VALIDATION', capabilities: ['PREPARE_RESOLUTION'], reasonKey: 'dashboard.awaitingChair', priority: 60 },
  { status: 'IN_REVIEW', capabilities: ['VIEW_ALL_REVIEWS'], reasonKey: 'dashboard.reviewsInProgress', priority: 70 },
  { status: 'AWAITING_RESEARCHER', capabilities: ['PUBLISH_OBSERVATIONS', 'VIEW_PUBLISHED_OBSERVATIONS'], reasonKey: 'cases.waitingResearcher', priority: 80 }
];

export const EXPIRY_ATTENTION = {
  capabilities: ['VIEW_ALL_REVIEWS'] as const,
  reasonKey: 'dashboard.reviewExpiry',
  priority: 90
};

export function committeeWorkHome(user: User | null | undefined): boolean {
  if (!user) {
    return false;
  }
  return rolesOf(user).some((role) => {
    if (ROLE_SCOPE[role] !== 'COMMITTEE') {
      return false;
    }
    return ROLE_CAPABILITIES[role].some((capability) => WORK_HOME_CAPABILITIES.includes(capability));
  });
}

/**
 * Inicio de quien trabaja sobre sus propios proyectos.
 * No depende del nombre del rol: usa el alcance OWN_PROJECT.
 * Si además tiene un inicio de comité, ese tiene prioridad.
 */
export function ownsProjectHome(user: User | null | undefined): boolean {
  if (!user || committeeWorkHome(user)) {
    return false;
  }
  return rolesOf(user).some((role) =>
    ROLE_SCOPE[role] === 'OWN_PROJECT' && ROLE_CAPABILITIES[role].includes('VIEW_PROJECTS')
  );
}

/** Motivos de atención del dueño del proyecto. No son estados nuevos. */
export const OWN_ATTENTION_RULES: readonly AttentionRule[] = [
  { status: 'AWAITING_RESEARCHER', capabilities: ['VIEW_PUBLISHED_OBSERVATIONS'], reasonKey: 'dashboard.respondObservations', priority: 10 },
  { status: 'WITH_OBSERVATIONS', capabilities: ['VIEW_PUBLISHED_OBSERVATIONS'], reasonKey: 'dashboard.respondObservations', priority: 10 },
  { status: 'PENDING_DOCUMENTS', capabilities: ['VIEW_DOCUMENTS'], reasonKey: 'dashboard.completeRecords', priority: 20 }
];

export const OWN_EXPIRY_ATTENTION = {
  capabilities: ['VIEW_PROJECTS'] as const,
  reasonKey: 'dashboard.approvalExpiring',
  priority: 30
};

export function ownAttentionFor(
  status: CommitteeCaseStatus,
  allowed: (capability: Capability) => boolean
): { reasonKey: string; priority: number } | null {
  return OWN_ATTENTION_RULES.find((rule) => rule.status === status && rule.capabilities.some(allowed)) ?? null;
}

export function attentionFor(
  status: CommitteeCaseStatus,
  allowed: (capability: Capability) => boolean
): { reasonKey: string; priority: number } | null {
  return ATTENTION_RULES.find((rule) => rule.status === status && rule.capabilities.some(allowed)) ?? null;
}
