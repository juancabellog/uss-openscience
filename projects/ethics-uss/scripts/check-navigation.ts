import { readFileSync } from 'node:fs';
import { YAML_CAPABILITIES } from '../src/app/features/dashboard/home-institutional.ts';
import { resolveOpenSections, toggleStoredSection, visibleNavigation, type NavSection } from '../src/app/core/layout/navigation.ts';

function check(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

function paths(capabilities: readonly string[], hide = false): string[] {
  return visibleNavigation(new Set(capabilities), { hideUnassignedCasePaths: hide })
    .flatMap((section) => section.items.map((item) => item.path));
}

function sections(capabilities: readonly string[], hide = false): string[] {
  return visibleNavigation(new Set(capabilities), { hideUnassignedCasePaths: hide }).map((section) => section.id);
}

function sectionItems(capabilities: readonly string[], id: string): string[] {
  return visibleNavigation(new Set(capabilities)).find((section) => section.id === id)?.items.map((item) => item.path) ?? [];
}

const admin = YAML_CAPABILITIES.ADMINISTRATOR;
check(sections(admin).join() === 'home,management,evaluation,followUp,administration', 'A. administrator sees the grouped menu');
check(sectionItems(admin, 'management').join() === '/solicitudes,/proyectos', 'A. administrator management follows capabilities, without CREATE_REQUEST');
check(sectionItems(admin, 'evaluation').join() === '/comites,/sesiones,/revisores', 'A. evaluation groups committees, sessions and the reviewer pool');
check(sectionItems(admin, 'administration').join() === '/usuarios,/configuracion,/auditoria', 'A. administration groups users, settings and audit');
check(!paths(admin).includes('/notificaciones'), 'A. notifications stay out of the sidebar');

const researcher = YAML_CAPABILITIES.RESEARCHER;
check(sections(researcher).join() === 'home,management', 'B. researcher only sees groups that have an item');
check(sectionItems(researcher, 'management').join() === '/proyectos,/proyectos/nuevo', 'B. researcher gets projects and new application');
check(!sections(researcher).includes('administration'), 'B. an empty administration heading is hidden');
check(!sections(researcher).includes('evaluation'), 'B. an empty evaluation heading is hidden');
check(!sections(researcher).includes('followUp'), 'B. expirations stay hidden without committee sessions');

const reviewer = YAML_CAPABILITIES.REVIEWER;
check(!paths(reviewer).includes('/revisores'), 'C. the reviewer pool is not the reviewer personal menu');
check(!sections(reviewer).includes('administration'), 'C. a reviewer does not get administration');
check(sectionItems(reviewer, 'myReviews').join() === '/revisiones,/historial', 'C. personal reviews stay in their own group');
check(sections(reviewer, true).join() === 'home', 'C. a case reviewer without an assignment keeps the existing restriction');

const president = YAML_CAPABILITIES.COMMITTEE_PRESIDENT;
check(sectionItems(president, 'evaluation').join() === '/sesiones,/revisores', 'D. president evaluation follows capabilities');
check(sections(president).includes('followUp'), 'D. president keeps expirations');
check(!sections(president).includes('administration'), 'D. president without admin capabilities has no administration group');

const union = [...new Set([...YAML_CAPABILITIES.RESEARCHER, ...YAML_CAPABILITIES.REVIEWER])];
const unionPaths = paths(union);
check(new Set(unionPaths).size === unionPaths.length, 'E. a combined capability set does not duplicate entries');
check(unionPaths.includes('/proyectos/nuevo') && unionPaths.includes('/revisiones'), 'E. the union keeps both research and review entries');

const onlyUsers = visibleNavigation(new Set(['MANAGE_USERS']));
check(onlyUsers.some((section) => section.id === 'administration' && section.items.length === 1), 'G. administration appears when it has one item');
check(!visibleNavigation(new Set(['VIEW_PROJECTS'])).some((section) => section.id === 'administration'), 'G. administration disappears with its last item');

const action = visibleNavigation(new Set(['CREATE_REQUEST'])).flatMap((section) => section.items).find((item) => item.path === '/proyectos/nuevo');
check(action?.action === true, 'new application stays an action inside management');
check(!paths(YAML_CAPABILITIES.ADMINISTRATOR).some((path) => path === '/notificaciones'), 'notifications are not a sidebar item');

const menu = visibleNavigation(new Set(admin));
const closedAdmin = { management: true, evaluation: false, followUp: false, administration: false };
let stored: Record<string, boolean> | null = { ...closedAdmin };
stored = toggleStoredSection(stored, menu, 'management', '/');
check(resolveOpenSections(menu, '/', stored).management === false, '1. Gestión can collapse');
stored = toggleStoredSection(stored, menu, 'management', '/');
check(resolveOpenSections(menu, '/', stored).management === true, '2. Gestión can expand again');
stored = toggleStoredSection(stored, menu, 'administration', '/');
check(resolveOpenSections(menu, '/', stored).management === true && resolveOpenSections(menu, '/', stored).administration === true, '3. several sections stay open together');
check(resolveOpenSections(menu, '/usuarios', { administration: false }).administration === true, '4. /usuarios opens Administración even when it was closed');
check(resolveOpenSections(menu, '/proyectos/42', { management: false }).management === true, '5. a project route opens Gestión');
check(resolveOpenSections(menu, '/', { followUp: true }).followUp === true, '6. a saved open section is restored');
check(sectionIdMissing(visibleNavigation(new Set(['VIEW_PROJECTS'])), 'administration'), '7. a capability change removes an empty section');
check(visibleNavigation(new Set(['VIEW_PROJECTS'])).some((section) => section.id === 'management') && !sectionItems(['VIEW_PROJECTS'], 'management').includes('/proyectos/nuevo'), '7. Nueva solicitud disappears without CREATE_REQUEST');

const shell = readFileSync(new URL('./../src/app/core/layout/app-shell.component.html', import.meta.url), 'utf8');
check(shell.includes('type="button"') && shell.includes('aria-expanded') && shell.includes('aria-controls'), '8. section headers are buttons with expanded state');
check(!shell.includes('<div (click)'), '8. the header is not a clickable div');
const sidebar = shell.slice(0, shell.indexOf('<div class="main">'));
check(!sidebar.includes('/notificaciones'), 'notifications stay out of the sidebar');

console.log('check-navigation OK');

function sectionIdMissing(sections: NavSection[], id: string): boolean {
  return !sections.some((section) => section.id === id);
}
