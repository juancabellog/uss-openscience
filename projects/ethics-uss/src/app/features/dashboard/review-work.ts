/**
 * Trabajo de revisión. La fuente es el estado de ReviewerAssignment.
 * RoleAssignment no clasifica estas filas.
 *
 * Estados reales del prototipo: PENDING, ACCEPTED, REJECTED, CONFLICT, COMPLETED.
 * PENDING es la bandeja Pendientes. ACCEPTED es En revisión.
 * COMPLETED, REJECTED y CONFLICT no piden intervención.
 */
export type ReviewWorkStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CONFLICT' | 'COMPLETED';

export function needsReviewerAction(status: ReviewWorkStatus): boolean {
  return status === 'PENDING' || status === 'ACCEPTED';
}

export function reviewAttentionCopy(status: ReviewWorkStatus): { reasonKey: string; statusKey: string } | null {
  if (status === 'PENDING') {
    return { reasonKey: 'dashboard.acceptReview', statusKey: 'dashboard.pending' };
  }
  if (status === 'ACCEPTED') {
    return { reasonKey: 'dashboard.completeReview', statusKey: 'dashboard.inReview' };
  }
  return null;
}
