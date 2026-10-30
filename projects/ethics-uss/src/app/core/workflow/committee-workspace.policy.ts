import { CommitteeCaseStatus, Role } from '../models/ethics.models';
import {
  canAssignReviewers,
  canConsolidate,
  canPrepareResolution,
  canValidateResolution,
  sectionsFor,
  seesInternalReviews
} from './case-workflow';

export type HistoryVisibility = 'operational' | 'external';

/**
 * Visibilidad ya presente en el expediente y en el detalle de proyecto.
 * No agrega ni quita reglas: combina las condiciones que esas pantallas ya evalúan.
 */
export interface CommitteeWorkspaceVisibility {
  admissibility: boolean;
  assignment: boolean;
  internalReviews: boolean;
  consolidate: boolean;
  ownReview: boolean;
  answerObservations: boolean;
  prepareResolution: boolean;
  validateResolution: boolean;
  /** Resolución no emitida. En el expediente solo la ven secretaría, presidencia y administración. */
  draftResolution: boolean;
  history: HistoryVisibility;
}

const EXTERNAL_ACTIONS = new Set([
  'DOCUMENT_UPLOADED',
  'DOCUMENT_VERSION_UPLOADED',
  'UPLOAD_DOCUMENT',
  'NOTIFY_COMMITTEE',
  'COMMITTEE_NOTIFIED',
  'COMMITTEE_NOTIFICATION',
  'COMMITTEE_CONTACT',
  'OBSERVATIONS_PUBLISHED',
  'PUBLISH_OBSERVATIONS',
  'OBSERVATION_NOTICE',
  'ANSWER_OBSERVATIONS',
  'SUBMIT_RESPONSES',
  'RESEARCHER_RESPONSE_SUBMITTED',
  'ISSUE_RESOLUTION',
  'RESOLUTION_VALIDATED',
  'VALIDATE_RESOLUTION',
  'AMENDMENT_REQUESTED',
  'FOLLOW_UP_REQUESTED',
  'RETURN_CASE'
]);

export function workspaceVisibility(
  role: Role | undefined,
  status: CommitteeCaseStatus,
  assignedMember: boolean
): CommitteeWorkspaceVisibility {
  const sections = sectionsFor(status);
  return {
    admissibility: sections.admissibility && canAssignReviewers(role),
    assignment: sections.assignment && canAssignReviewers(role),
    internalReviews: sections.reviewWorkspace && seesInternalReviews(role),
    consolidate: sections.reviewWorkspace && canConsolidate(role),
    ownReview: assignedMember,
    answerObservations: false,
    prepareResolution: sections.resolutionForm && canPrepareResolution(role) && status !== 'PENDING_PRESIDENT_VALIDATION',
    validateResolution: status === 'PENDING_PRESIDENT_VALIDATION' && canValidateResolution(role),
    draftResolution: canPrepareResolution(role) || canValidateResolution(role),
    history: 'operational'
  };
}

export function historyEventVisible(action: string, visibility: HistoryVisibility): boolean {
  return visibility === 'operational' || EXTERNAL_ACTIONS.has(action);
}

/**
 * El paquete ya evaluado sigue congelado.
 * Durante AWAITING_RESEARCHER el investigador dueño puede preparar la respuesta
 * con documentos nuevos o versiones nuevas, sin reemplazar las anteriores.
 */
export function researcherResponseOpen(input: {
  canRespond: boolean;
  isOwner: boolean;
  status: CommitteeCaseStatus;
}): boolean {
  return input.canRespond && input.isOwner && input.status === 'AWAITING_RESEARCHER';
}

export function caseDocumentsEditable(input: {
  canRespond: boolean;
  isOwner: boolean;
  status: CommitteeCaseStatus;
  packageFrozen: boolean;
}): boolean {
  if (researcherResponseOpen(input)) {
    return true;
  }
  return !input.packageFrozen;
}
