import { Injectable } from '@angular/core';
import { reviewFormFor } from '../forms/review-form.definition';
import { Review, Role } from '../models/ethics.models';
import { AuditService } from './audit.service';
import { CommitteeCaseService } from './committee-case.service';
import { EthicsStore } from './ethics-store.service';
import { ReviewerService } from './reviewer.service';
import { nowIso, uid } from './ids';

@Injectable({ providedIn: 'root' })
export class ReviewService {
  constructor(
    private readonly store: EthicsStore,
    private readonly audit: AuditService,
    private readonly assignments: ReviewerService,
    private readonly cases: CommitteeCaseService
  ) {}

  visibleTo(assignmentId: string, userId: string, role: Role): Review | undefined {
    const assignment = this.assignments.getAssignment(assignmentId);
    if (!assignment) {
      return undefined;
    }
    const review = this.store.snapshot().reviews.find((item) => item.assignmentId === assignmentId);
    if (!review) {
      return undefined;
    }
    if (role === 'RESEARCHER') {
      return undefined;
    }
    if (role === 'COMMITTEE_MEMBER' && review.reviewerId !== userId) {
      return undefined;
    }
    return review;
  }

  listForCase(caseId: string, userId: string, role: Role, cycleNumber?: number): Review[] {
    const items = this.store.snapshot().reviews.filter((item) =>
      item.caseId === caseId && (cycleNumber === undefined || item.cycleNumber === cycleNumber)
    );
    if (role === 'RESEARCHER') {
      return [];
    }
    if (role === 'COMMITTEE_MEMBER') {
      return items.filter((item) => item.reviewerId === userId);
    }
    return items;
  }

  save(assignmentId: string, criteria: Record<string, string>, submit: boolean): Review {
    const assignment = this.assignments.getAssignment(assignmentId);
    if (!assignment) {
      throw new Error('Asignación no encontrada');
    }
    const current = this.cases.get(assignment.caseId);
    const existing = this.store.snapshot().reviews.find((item) =>
      item.assignmentId === assignmentId && item.cycleNumber === (current?.currentCycle ?? 1) && !item.submittedAt
    ) ?? this.store.snapshot().reviews.find((item) =>
      item.assignmentId === assignmentId && item.cycleNumber === (current?.currentCycle ?? 1)
    );
    const review: Review = {
      id: existing?.id ?? uid('rev'),
      assignmentId,
      caseId: assignment.caseId,
      reviewerId: assignment.reviewerId,
      cycleNumber: current?.currentCycle ?? 1,
      criteria,
      submittedAt: submit ? nowIso() : existing?.submittedAt,
      privacy: 'COMMITTEE_ONLY',
      formDefinitionId: current ? reviewFormFor(current.committee).id : 'CEC_REVIEW_V1'
    };
    this.store.update((state) => ({
      ...state,
      reviews: [review, ...state.reviews.filter((item) => item.id !== review.id)],
      assignments: submit
        ? state.assignments.map((item) => (item.id === assignmentId ? { ...item, status: 'COMPLETED' } : item))
        : state.assignments
    }));
    if (submit) {
      this.audit.record({
        action: 'REVIEW_SUBMITTED',
        objectType: 'Review',
        objectId: review.id,
        description: 'Envió revisión',
        projectId: this.cases.get(assignment.caseId)?.projectId
      });
      const done = this.assignments.byCase(assignment.caseId).every((item) => item.status === 'COMPLETED' || item.id === assignmentId);
      if (done) {
        this.cases.setStatus(assignment.caseId, 'IN_REVIEW');
      }
    }
    return review;
  }
}
