import { CommitteeCaseStatus, Role } from '../models/ethics.models';
import { ROLE_CAPABILITIES } from './role-capabilities';

export interface CaseSections {
  admissibility: boolean;
  assignment: boolean;
  reviewWorkspace: boolean;
  resolutionForm: boolean;
  resolutionRead: boolean;
}

const EARLY: CommitteeCaseStatus[] = ['NOTIFIED', 'ADMISSIBILITY', 'RETURNED'];
/**
 * PENDING_ASSIGNMENT queda donde el flujo ya salía de la admisibilidad
 * (CommitteeCaseService.admit) y todavía no hay dos revisores vigentes.
 * No se agregó un estado nuevo de admisibilidad.
 * PENDING_ACCEPTANCE es el paso siguiente, hasta que ambos aceptan e ingresan a IN_REVIEW.
 */
const ASSIGNMENT: CommitteeCaseStatus[] = [...EARLY, 'PENDING_ASSIGNMENT', 'PENDING_ACCEPTANCE'];
const REVIEW: CommitteeCaseStatus[] = ['IN_REVIEW', 'PENDING_SESSION', 'WITH_OBSERVATIONS', 'AWAITING_RESEARCHER'];
const RESOLUTION_FORM: CommitteeCaseStatus[] = ['PENDING_RESOLUTION', 'PENDING_PRESIDENT_VALIDATION'];
const CLOSED: CommitteeCaseStatus[] = ['APPROVED', 'REJECTED', 'EXEMPT', 'WITHDRAWN'];

export function sectionsFor(status: CommitteeCaseStatus): CaseSections {
  return {
    admissibility: EARLY.includes(status),
    assignment: ASSIGNMENT.includes(status),
    reviewWorkspace: REVIEW.includes(status),
    resolutionForm: RESOLUTION_FORM.includes(status),
    resolutionRead: RESOLUTION_FORM.includes(status) || CLOSED.includes(status) || REVIEW.includes(status)
  };
}

function allows(role: Role | null | undefined, capability: (typeof ROLE_CAPABILITIES)['ADMINISTRATOR'][number]): boolean {
  return !!role && ROLE_CAPABILITIES[role].includes(capability);
}

export function canAssignReviewers(role?: Role | null): boolean {
  return allows(role, 'ASSIGN_REVIEWERS');
}

export function canConsolidate(role?: Role | null): boolean {
  return allows(role, 'CONSOLIDATE_OBSERVATIONS');
}

export function canPrepareResolution(role?: Role | null): boolean {
  return allows(role, 'PREPARE_RESOLUTION');
}

export function canValidateResolution(role?: Role | null): boolean {
  return allows(role, 'VALIDATE_RESOLUTION');
}

export function seesInternalReviews(role?: Role | null): boolean {
  return allows(role, 'VIEW_ALL_REVIEWS');
}
