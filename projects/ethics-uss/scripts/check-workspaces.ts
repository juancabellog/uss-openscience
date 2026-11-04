import { readFileSync } from 'node:fs';
import { buildWorkspaces, chooseWorkspace, workspaceAllows, type FunctionView } from '../src/app/core/layout/workspace.ts';

function check(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

const researcher: FunctionView = { roleId: 'RESEARCHER', status: 'ACTIVE' };
const reviewer: FunctionView = { roleId: 'REVIEWER', status: 'ACTIVE' };
const completedReviewer: FunctionView = { roleId: 'REVIEWER', status: 'COMPLETED' };
const revokedReviewer: FunctionView = { roleId: 'REVIEWER', status: 'REVOKED' };
const admin: FunctionView = { roleId: 'ADMINISTRATOR', status: 'ACTIVE' };
const cec: FunctionView = { roleId: 'COMMITTEE_PRESIDENT', scopeType: 'COMMITTEE', scopeId: 'CEC', status: 'ACTIVE' };
const cecua: FunctionView = { roleId: 'COMMITTEE_PRESIDENT', scopeType: 'COMMITTEE', scopeId: 'CECUA', status: 'ACTIVE' };
const secretary: FunctionView = { roleId: 'EXECUTIVE_SECRETARY', scopeType: 'COMMITTEE', scopeId: 'CEC', status: 'ACTIVE' };

const onlyResearcher = buildWorkspaces([researcher]);
check(onlyResearcher.length === 1 && onlyResearcher[0].kind === 'RESEARCHER', 'A keeps a single researcher workspace');
check(workspaceAllows('RESEARCHER', '/proyectos') && !workspaceAllows('RESEARCHER', '/revisiones'), 'A researcher menu stays on own projects');

const both = buildWorkspaces([researcher, reviewer, reviewer]);
check(both.map((item) => item.kind).join() === 'RESEARCHER,REVIEWER', 'B collapses reviewer assignments into one workspace');
check(!workspaceAllows('REVIEWER', '/proyectos') && workspaceAllows('REVIEWER', '/revisiones'), 'B reviewer menu does not lead with own projects');
check(!workspaceAllows('REVIEWER', '/usuarios'), 'selecting reviewer does not open user administration');

const researcherAdmin = buildWorkspaces([researcher, admin]);
check(researcherAdmin.map((item) => item.id).join() === 'RESEARCHER,ADMINISTRATOR', 'C offers researcher and administration');
check(workspaceAllows('ADMINISTRATOR', '/usuarios') === true, 'administration can show users only as a presentation path');

const oneChair = buildWorkspaces([cecua]);
check(oneChair.length === 1 && oneChair[0].id === 'COMMITTEE_PRESIDENT:CECUA', 'D keeps the CECUA presidency scope');

const twoChairs = buildWorkspaces([cec, cecua]);
check(twoChairs.map((item) => item.scopeId).join() === 'CEC,CECUA', 'E keeps one workspace per committee');

const external = buildWorkspaces([completedReviewer]);
check(external.length === 1 && external[0].kind === 'REVIEWER', 'F keeps a reviewer workspace for a visible completed review');
check(!workspaceAllows('REVIEWER', '/proyectos/nuevo'), 'F does not present a new application');

const revoked = buildWorkspaces([researcher, { ...cecua, status: 'REVOKED' }]);
check(revoked.length === 1 && revoked[0].kind === 'RESEARCHER', 'G drops a revoked presidency');
check(chooseWorkspace(revoked, 'COMMITTEE_PRESIDENT:CECUA')?.kind === 'RESEARCHER', 'G falls back when the stored workspace is gone');
check(chooseWorkspace(both, 'REVIEWER')?.kind === 'REVIEWER', 'a stored valid workspace wins over the default');
check(chooseWorkspace(researcherAdmin, null)?.kind === 'RESEARCHER', 'default prefers researcher');
check(buildWorkspaces([revokedReviewer]).length === 0, 'a revoked review is not a workspace');
check(buildWorkspaces([], true).length === 1, 'VIEW_OWN_REVIEW still offers one reviewer workspace');
check(buildWorkspaces([secretary, admin]).map((item) => item.kind).join() === 'EXECUTIVE_SECRETARY,ADMINISTRATOR', 'secretary stays scoped and does not become administration');

const navigation = readFileSync(new URL('../src/app/core/layout/navigation.ts', import.meta.url), 'utf8');
const service = readFileSync(new URL('../src/app/core/layout/workspace.service.ts', import.meta.url), 'utf8');
check(navigation.includes('item.when(capabilities)') && navigation.includes('workspaceAllows'), 'navigation keeps the capability filter and adds the workspace filter');
check(service.includes('WORKSPACE_STORAGE_KEY') && !service.includes('selectedRole'), 'the preference stays in the browser');

console.log('check-workspaces OK');
