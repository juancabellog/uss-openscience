import type { AssignmentStatus, ReviewerAssignment } from '../../core/models/ethics.models';

/** Mismos estados que las calugas del Inicio. ACCEPTED es «En curso». COMPLETED es histórico. */
export type ReviewInboxFilter = '' | 'PENDING' | 'ACCEPTED' | 'COMPLETED';

export function reviewInboxFilter(value: string | null | undefined): ReviewInboxFilter {
  return value === 'PENDING' || value === 'ACCEPTED' || value === 'COMPLETED' ? value : '';
}

export function matchesReviewFilter(status: AssignmentStatus, filter: ReviewInboxFilter): boolean {
  return !filter || status === filter;
}

export function countReviews(items: readonly ReviewerAssignment[], filter: ReviewInboxFilter): number {
  return items.filter((item) => matchesReviewFilter(item.status, filter)).length;
}

export function pendingReviews(items: readonly ReviewerAssignment[]): number {
  return countReviews(items, 'PENDING');
}

export function activeReviews(items: readonly ReviewerAssignment[]): number {
  return countReviews(items, 'ACCEPTED');
}

export function completedReviews(items: readonly ReviewerAssignment[]): number {
  return countReviews(items, 'COMPLETED');
}
