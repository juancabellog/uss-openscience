import { Injectable } from '@angular/core';
import { CommitteeCase, CommitteeCaseStatus, EthicsCommittee, User } from '../models/ethics.models';
import {
  Capability,
  ROLE_CAPABILITIES,
  ROLE_SCOPE,
  rolesOf
} from '../workflow/role-capabilities';
import { isOperationalReviewStatus, isReadableReviewStatus } from '../workflow/review-assignment-status';
import { EthicsStore } from './ethics-store.service';

export interface PermissionContext {
  projectId?: string;
  committeeCaseId?: string;
  committeeType?: EthicsCommittee;
  workflowStatus?: CommitteeCaseStatus;
}

const REVIEW_EDIT_STATUSES: CommitteeCaseStatus[] = ['IN_REVIEW', 'PENDING_ACCEPTANCE'];
const REVIEWER_OPERATION = new Set([
  'EDIT_OWN_REVIEW',
  'SUBMIT_OWN_REVIEW',
  'ACCEPT_REVIEW_ASSIGNMENT',
  'REJECT_REVIEW_ASSIGNMENT'
]);

/**
 * Resuelve la interfaz del prototipo. No sustituye a AuthorizationService:
 * el servidor vuelve a autorizar con el usuario de OpenScience, el alcance y el estado.
 */
@Injectable({ providedIn: 'root' })
export class PermissionService {
  constructor(private readonly store: EthicsStore) {}

  can(user: User | null | undefined, capability: Capability, context: PermissionContext = {}): boolean {
    if (!user) {
      return false;
    }
    if (user.grantedCapabilities) {
      return user.grantedCapabilities.includes(capability);
    }
    return rolesOf(user).some((role) => this.grants(user, role, capability, context));
  }

  effectiveCapabilities(user: User | null | undefined, context: PermissionContext = {}): Capability[] {
    if (!user) {
      return [];
    }
    const granted = new Set<Capability>();
    for (const role of rolesOf(user)) {
      for (const capability of ROLE_CAPABILITIES[role]) {
        if (this.grants(user, role, capability, context)) {
          granted.add(capability);
        }
      }
    }
    return [...granted];
  }

  private grants(user: User, role: User['role'], capability: Capability, context: PermissionContext): boolean {
    if (!ROLE_CAPABILITIES[role].includes(capability)) {
      return false;
    }
    if (!this.scopeMatches(user, role, capability, context)) {
      return false;
    }
    if (capability === 'VIEW_OWN_REVIEW' && !this.hasReviewAssignment(user, context, isReadableReviewStatus)) {
      return false;
    }
    if (capability === 'EDIT_OWN_REVIEW' || capability === 'SUBMIT_OWN_REVIEW') {
      if (!this.hasReviewAssignment(user, context, (status) => status === 'ACCEPTED')) {
        return false;
      }
      const status = this.statusOf(context);
      if (!status || !REVIEW_EDIT_STATUSES.includes(status)) {
        return false;
      }
    }
    if (capability === 'ACCEPT_REVIEW_ASSIGNMENT' || capability === 'REJECT_REVIEW_ASSIGNMENT') {
      return this.hasReviewAssignment(user, context, (status) => status === 'PENDING');
    }
    if (capability === 'SUBMIT_REQUEST' && ROLE_SCOPE[role] === 'GLOBAL' && !this.projectIdOf(context)) {
      return false;
    }
    return true;
  }

  private scopeMatches(user: User, role: User['role'], capability: Capability, context: PermissionContext): boolean {
    const scope = ROLE_SCOPE[role];
    if (scope === 'GLOBAL') {
      return true;
    }
    if (scope === 'OWN_PROJECT') {
      const projectId = this.projectIdOf(context);
      if (!projectId) {
        return capability === 'CREATE_REQUEST' || capability === 'SUBMIT_REQUEST';
      }
      const project = this.store.snapshot().projects.find((item) => item.id === projectId);
      return project?.researcherId === user.id;
    }
    if (scope === 'COMMITTEE') {
      if (context.committeeType) {
        return user.committees.includes(context.committeeType);
      }
      const cases = this.casesOf(context);
      if (cases.length) {
        return cases.some((item) => user.committees.includes(item.committee));
      }
      return user.committees.length > 0;
    }
    if (REVIEWER_OPERATION.has(capability)) {
      return this.hasReviewAssignment(user, context, isOperationalReviewStatus);
    }
    return this.hasReviewAssignment(user, context, isReadableReviewStatus);
  }

  /**
   * Solo ReviewerAssignment. reviewerIds del expediente es una copia que
   * escribe ReviewerService al asignar; no concede acceso por sí sola.
   * Operativa: PENDING o ACCEPTED. Histórica: COMPLETED, solo lectura.
   */
  private hasReviewAssignment(
    user: User,
    context: PermissionContext,
    accepts: (status: string) => boolean
  ): boolean {
    const caseIds = context.committeeCaseId
      ? [context.committeeCaseId]
      : this.casesOf(context).map((item) => item.id);
    return this.store.snapshot().assignments.some((item) =>
      caseIds.includes(item.caseId)
      && item.reviewerId === user.id
      && !item.supersededAt
      && accepts(item.status)
    );
  }

  private statusOf(context: PermissionContext): CommitteeCaseStatus | undefined {
    if (context.workflowStatus) {
      return context.workflowStatus;
    }
    return this.casesOf(context).find((item) => item.id === context.committeeCaseId)?.status;
  }

  private projectIdOf(context: PermissionContext): string | undefined {
    if (context.projectId) {
      return context.projectId;
    }
    return this.store.snapshot().cases.find((item) => item.id === context.committeeCaseId)?.projectId;
  }

  private casesOf(context: PermissionContext): CommitteeCase[] {
    const cases = this.store.snapshot().cases;
    if (context.committeeCaseId) {
      const item = cases.find((row) => row.id === context.committeeCaseId);
      return item ? [item] : [];
    }
    const projectId = this.projectIdOf(context);
    if (projectId) {
      return cases.filter((item) => item.projectId === projectId);
    }
    if (context.committeeType) {
      return cases.filter((item) => item.committee === context.committeeType);
    }
    return [];
  }
}
