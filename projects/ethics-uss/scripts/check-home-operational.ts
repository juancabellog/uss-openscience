import { capabilitiesForRoles } from '../src/app/features/dashboard/home-institutional.ts';
import { operationalTiles, showCommitteeProjects } from '../src/app/features/dashboard/home-tiles.ts';
import { needsReviewerAction, reviewAttentionCopy } from '../src/app/features/dashboard/review-work.ts';
import { projectBelongsToCommittees } from '../src/app/core/workflow/committee-projects.ts';
import { isHistoricalReviewStatus, isOperationalReviewStatus, isReadableReviewStatus } from '../src/app/core/workflow/review-assignment-status.ts';
import { activeReviews, completedReviews, pendingReviews } from '../src/app/features/reviews/review-inbox.filter.ts';

function check(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

const ignacio = capabilitiesForRoles(['REVIEWER']);
const sofia = capabilitiesForRoles(['REVIEWER']);
const alberto = capabilitiesForRoles(['REVIEWER']);

const ignacioTiles = operationalTiles(ignacio, showCommitteeProjects(ignacio, ['CECUA'], false));
check(
  ignacioTiles.map((item) => item.id).join(',') === 'pending-reviews,reviews-in-progress',
  'Ignacio muestra pendientes y en curso, sin proyectos del comité'
);
check(!ignacioTiles.some((item) => item.id === 'committee-projects'), 'Ignacio no ve proyectos del comité');
check(!ignacioTiles.some((item) => item.id === 'assigned-reviews'), 'el total histórico no es un indicador del inicio');
check(new Set(ignacioTiles.map((item) => item.id)).size === ignacioTiles.length, 'sin módulos duplicados');
check(!ignacioTiles.some((item) => item.capability.startsWith('SHOW_')), 'sin capabilities de presentación');
check(!ignacio.has('VIEW_COMMITTEE_SESSIONS') && !ignacio.has('VIEW_REVIEWER_POOL'), 'el revisor no abre sesiones ni el pool');

const sofiaTiles = operationalTiles(sofia, showCommitteeProjects(sofia, ['CECUA'], false));
check(sofiaTiles.map((item) => item.id).join(',') === 'pending-reviews,reviews-in-progress', 'Sofía consulta como revisor, sin proyectos del comité');
check(sofia.has('VIEW_OWN_REVIEW'), 'la consulta histórica usa VIEW_OWN_REVIEW');
check(!sofia.has('VIEW_COMMITTEE_SESSIONS'), 'Sofía no hereda sesiones de comité');

const albertoTiles = operationalTiles(alberto, showCommitteeProjects(alberto, [], false));
check(
  albertoTiles.map((item) => item.id).join(',') === 'pending-reviews,reviews-in-progress',
  'Alberto no ve proyectos del comité'
);

const ignacioReviews = [
  { status: 'COMPLETED' as const },
  { status: 'COMPLETED' as const }
];
check(pendingReviews(ignacioReviews) === 0, 'Ignacio pendientes');
check(activeReviews(ignacioReviews) === 0, 'Ignacio en curso');
check(completedReviews(ignacioReviews) === 2, 'Ignacio históricas');
check(ignacioReviews.every((item) => !needsReviewerAction(item.status)), 'completadas no piden acción');
check(ignacioReviews.every((item) => !isOperationalReviewStatus(item.status) && isHistoricalReviewStatus(item.status)), 'COMPLETED no es operativa');
check(ignacioReviews.every((item) => isReadableReviewStatus(item.status)), 'COMPLETED sigue siendo consultable');

const albertoActive = [{ status: 'ACCEPTED' as const }];
check(activeReviews(albertoActive) === 1, 'Alberto en curso');
check(pendingReviews(albertoActive) === 0, 'Alberto sin pendientes');
check(reviewAttentionCopy('ACCEPTED')?.reasonKey === 'dashboard.completeReview', 'en curso pide completar');
const albertoDone = [{ status: 'COMPLETED' as const }];
check(activeReviews(albertoDone) === 0 && pendingReviews(albertoDone) === 0, 'al completar sale del trabajo activo');
check(completedReviews(albertoDone) === 1, 'queda una histórica');
check(reviewAttentionCopy('COMPLETED') === null, 'completada no arma fila de atención');
check(reviewAttentionCopy('PENDING')?.reasonKey === 'dashboard.acceptReview', 'pendiente pide aceptar');

const projects = [
  { id: 'prj-1', committees: ['CEC', 'BIOSEGURIDAD', 'CECUA'] },
  { id: 'prj-2', committees: ['CECUA'] },
  { id: 'prj-4', committees: ['CEC'] }
];
const membership = new Set(['CECUA']);
const committeeIds = new Set(
  projects.filter((project) => projectBelongsToCommittees(project.committees, membership)).map((project) => project.id)
);
check(committeeIds.size === 2 && committeeIds.has('prj-1') && committeeIds.has('prj-2'), 'proyectos del comité, una vez por id');
check(!committeeIds.has('prj-4'), 'un expediente de otro comité no entra por reviewerIds');

console.log('home-operational.check OK');
