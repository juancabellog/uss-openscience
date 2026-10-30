/**
 * Módulos del inicio de revisor.
 * La capability es la de permissions-V2.yml. El nombre del rol no entra aquí.
 * El conteo de revisiones lo hace el llamador con ReviewerAssignment, no con RoleAssignment.
 */
export interface HomeTileDefinition {
  id: string;
  labelKey: string;
  ariaKey: string;
  priority: number;
  route: string;
  query: Record<string, string>;
  capability: string;
  countOf: 'committee-projects' | 'reviews';
}

export const HOME_TILES: readonly HomeTileDefinition[] = [
  {
    id: 'committee-projects',
    labelKey: 'dashboard.committeeProjects',
    ariaKey: 'dashboard.viewCommitteeProjects',
    priority: 10,
    route: '/proyectos',
    query: { scope: 'committee' },
    capability: 'VIEW_COMMITTEE_SESSIONS',
    countOf: 'committee-projects'
  },
  {
    id: 'pending-reviews',
    labelKey: 'dashboard.pendingReviews',
    ariaKey: 'dashboard.viewPendingReviews',
    priority: 20,
    route: '/revisiones',
    query: { status: 'PENDING' },
    capability: 'VIEW_OWN_REVIEW',
    countOf: 'reviews'
  },
  {
    id: 'reviews-in-progress',
    labelKey: 'dashboard.activeReviews',
    ariaKey: 'dashboard.viewReviewsInProgress',
    priority: 30,
    route: '/revisiones',
    query: { status: 'ACCEPTED' },
    capability: 'VIEW_OWN_REVIEW',
    countOf: 'reviews'
  }
];

/**
 * Proyectos del comité solo con una capability de sesión que el revisor no tiene.
 * La pertenencia institucional a un comité no abre ese módulo.
 */
export function showCommitteeProjects(
  capabilities: ReadonlySet<string>,
  committeeIds: readonly string[],
  occupiedHome: boolean
): boolean {
  return !occupiedHome
    && committeeIds.length > 0
    && capabilities.has('VIEW_COMMITTEE_SESSIONS')
    && !capabilities.has('VIEW_AUDIT');
}

export function operationalTiles(
  capabilities: ReadonlySet<string>,
  showProjects: boolean
): HomeTileDefinition[] {
  const seen = new Set<string>();
  return HOME_TILES.filter((tile) => {
    if (tile.countOf === 'committee-projects' && !showProjects) {
      return false;
    }
    if (!capabilities.has(tile.capability)) {
      return false;
    }
    if (seen.has(tile.id)) {
      return false;
    }
    seen.add(tile.id);
    return true;
  }).sort((a, b) => a.priority - b.priority);
}
