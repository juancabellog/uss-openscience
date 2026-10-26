import { CommitteeCaseStatus, Role } from '../models/ethics.models';

export interface CaseSections {
  admissibility: boolean;
  assignment: boolean;
  reviewWorkspace: boolean;
  resolutionForm: boolean;
  resolutionRead: boolean;
}

const EARLY: CommitteeCaseStatus[] = ['NOTIFIED', 'ADMISSIBILITY', 'RETURNED'];
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

export function canAssignReviewers(role?: Role | null): boolean {
  return role === 'EXECUTIVE_SECRETARY' || role === 'COMMITTEE_PRESIDENT' || role === 'ADMINISTRATOR';
}

export function canConsolidate(role?: Role | null): boolean {
  return role === 'EXECUTIVE_SECRETARY' || role === 'ADMINISTRATOR';
}

export function canPrepareResolution(role?: Role | null): boolean {
  return role === 'EXECUTIVE_SECRETARY' || role === 'ADMINISTRATOR';
}

export function canValidateResolution(role?: Role | null): boolean {
  return role === 'COMMITTEE_PRESIDENT' || role === 'ADMINISTRATOR';
}

export function seesInternalReviews(role?: Role | null): boolean {
  return role === 'COMMITTEE_PRESIDENT' || role === 'EXECUTIVE_SECRETARY' || role === 'ADMINISTRATOR';
}
