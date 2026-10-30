import type { AssignmentStatus, CommitteeCaseStatus } from '../models/ethics.models';

const REASSIGNABLE: ReadonlySet<AssignmentStatus> = new Set([
  'PENDING',
  'ACCEPTED',
  'REJECTED',
  'CONFLICT',
  'COMPLETED'
]);

/** La revisión sigue en su etapa operativa. */
const ORDINARY_CASE: ReadonlySet<CommitteeCaseStatus> = new Set([
  'NOTIFIED',
  'ADMISSIBILITY',
  'RETURNED',
  'PENDING_ASSIGNMENT',
  'PENDING_ACCEPTANCE',
  'IN_REVIEW',
  'PENDING_SESSION',
  'PENDING_DOCUMENTS'
]);

/**
 * El expediente ya salió de la revisión operativa: observaciones,
 * respuesta del investigador o resolución todavía abierta.
 */
const ADVANCED_CASE: ReadonlySet<CommitteeCaseStatus> = new Set([
  'WITH_OBSERVATIONS',
  'AWAITING_RESEARCHER',
  'PENDING_RESOLUTION',
  'PENDING_PRESIDENT_VALIDATION'
]);

const FINISHED_PROCESS: ReadonlySet<CommitteeCaseStatus> = new Set([
  'APPROVED',
  'REJECTED',
  'EXEMPT',
  'WITHDRAWN'
]);

export type ReassignCapability = 'REASSIGN_REVIEWER' | 'REASSIGN_REVIEWER_ADVANCED_STAGE';

export interface ReassignReviewerContext {
  hasOrdinaryCapability: boolean;
  hasAdvancedCapability: boolean;
  status?: AssignmentStatus;
  reviewSubmitted: boolean;
  caseStatus?: CommitteeCaseStatus;
}

/**
 * Etapa avanzada: la asignación que se reemplaza ya está COMPLETED,
 * ya hay una revisión enviada, o el expediente salió de la revisión operativa.
 * Un expediente cerrado no se trata como reasignable.
 */
export function isAdvancedReviewerReassignment(input: {
  caseStatus?: CommitteeCaseStatus;
  assignmentStatus?: AssignmentStatus;
  reviewSubmitted: boolean;
}): boolean {
  if (!input.caseStatus || FINISHED_PROCESS.has(input.caseStatus)) {
    return false;
  }
  if (input.reviewSubmitted || input.assignmentStatus === 'COMPLETED') {
    return true;
  }
  return ADVANCED_CASE.has(input.caseStatus);
}

/** Capability que hay que volver a comprobar. null: el cambio no está abierto. */
export function requiredReassignCapability(input: {
  caseStatus?: CommitteeCaseStatus;
  status?: AssignmentStatus;
  reviewSubmitted: boolean;
}): ReassignCapability | null {
  if (!input.caseStatus || !input.status || FINISHED_PROCESS.has(input.caseStatus) || !REASSIGNABLE.has(input.status)) {
    return null;
  }
  if (isAdvancedReviewerReassignment({
    caseStatus: input.caseStatus,
    assignmentStatus: input.status,
    reviewSubmitted: input.reviewSubmitted
  })) {
    return 'REASSIGN_REVIEWER_ADVANCED_STAGE';
  }
  return ORDINARY_CASE.has(input.caseStatus) ? 'REASSIGN_REVIEWER' : null;
}

/**
 * REASSIGN_REVIEWER no autoriza una etapa avanzada.
 * REASSIGN_REVIEWER_ADVANCED_STAGE no reemplaza a la ordinaria.
 */
export function canReassignReviewer(context: ReassignReviewerContext): boolean {
  const required = requiredReassignCapability(context);
  if (required === 'REASSIGN_REVIEWER') {
    return context.hasOrdinaryCapability;
  }
  if (required === 'REASSIGN_REVIEWER_ADVANCED_STAGE') {
    return context.hasAdvancedCapability;
  }
  return false;
}
