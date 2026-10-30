import { ApprovalSemaphore, CommitteeCase } from '../../core/models/ethics.models';
import { ProjectRow } from '../../core/services/project.service';

export type ValidityFilter = '' | ApprovalSemaphore;

export type ExpiryReader = (item: CommitteeCase) => { daysLeft: number; semaphore: ApprovalSemaphore } | null;

/**
 * La vigencia es del comité (expiryInfo), no un estado del proyecto.
 * El proyecto entra en el filtro si al menos un comité tiene ese semáforo.
 */
export function projectHasValidity(row: ProjectRow, validity: ValidityFilter, expiry: ExpiryReader): boolean {
  if (!validity) {
    return true;
  }
  return row.cases.some((item) => expiry(item)?.semaphore === validity);
}

export function projectHasStatus(row: ProjectRow, status: string): boolean {
  return !status || row.globalStatus === status;
}

export interface InventoryIndicator {
  id: string;
  labelKey: string;
  ariaKey: string;
  query: { status?: string; validity?: string };
  count: (rows: ProjectRow[], expiry: ExpiryReader) => number;
}

/**
 * Contadores del Inicio de proyectos propios. Cada uno usa el mismo
 * criterio que el filtro de Proyectos:
 * - projects: inventario visible, sin filtro.
 * - evaluation: estado global IN_EVALUATION. MIXED tiene filtro propio.
 * - observations: estado global WITH_OBSERVATIONS.
 * - validApprovals: proyectos con al menos un comité en semáforo VIGENTE.
 *   No cuenta comités ni el estado global APPROVED.
 * - expiring: proyectos con al menos un comité en semáforo PROXIMO.
 *   No cuenta cada comité como un proyecto.
 */
export const INVENTORY_INDICATORS: readonly InventoryIndicator[] = [
  {
    id: 'projects',
    labelKey: 'projects.title',
    ariaKey: 'dashboard.viewProjects',
    query: {},
    count: (rows) => rows.length
  },
  {
    id: 'evaluation',
    labelKey: 'dashboard.underReview',
    ariaKey: 'dashboard.viewProjectsUnderReview',
    query: { status: 'IN_EVALUATION' },
    count: (rows) => rows.filter((row) => row.globalStatus === 'IN_EVALUATION').length
  },
  {
    id: 'observations',
    labelKey: 'dashboard.withComments',
    ariaKey: 'dashboard.viewProjectsWithObservations',
    query: { status: 'WITH_OBSERVATIONS' },
    count: (rows) => rows.filter((row) => row.globalStatus === 'WITH_OBSERVATIONS').length
  },
  {
    id: 'validApprovals',
    labelKey: 'dashboard.activeApprovals',
    ariaKey: 'dashboard.viewValidApprovals',
    query: { validity: 'VIGENTE' },
    count: (rows, expiry) => rows.filter((row) => projectHasValidity(row, 'VIGENTE', expiry)).length
  },
  {
    id: 'expiring',
    labelKey: 'dashboard.expiringSoon',
    ariaKey: 'dashboard.viewProjectsExpiring',
    query: { validity: 'PROXIMO' },
    count: (rows, expiry) => rows.filter((row) => projectHasValidity(row, 'PROXIMO', expiry)).length
  }
];
