/**
 * Estado de ReviewerAssignment, no de RoleAssignment.
 * PENDING y ACCEPTED son trabajo actual. COMPLETED es consulta histórica.
 * REJECTED y CONFLICT no dan acceso.
 */
export type ReviewAssignmentWorkStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CONFLICT' | 'COMPLETED';

export function isOperationalReviewStatus(status: string): boolean {
  return status === 'PENDING' || status === 'ACCEPTED';
}

export function isHistoricalReviewStatus(status: string): boolean {
  return status === 'COMPLETED';
}

/** Abrir la revisión propia, incluida la ya enviada. No autoriza a editarla. */
export function isReadableReviewStatus(status: string): boolean {
  return isOperationalReviewStatus(status) || isHistoricalReviewStatus(status);
}
