export const WORKSPACE_STORAGE_KEY = 'ethics.active.workspace';

export type WorkspaceKind =
  | 'RESEARCHER'
  | 'REVIEWER'
  | 'COMMITTEE_PRESIDENT'
  | 'EXECUTIVE_SECRETARY'
  | 'ADMINISTRATOR';

export interface EthicsWorkspace {
  id: string;
  kind: WorkspaceKind;
  scopeType?: 'COMMITTEE';
  scopeId?: string;
  labelKey: string;
  descriptionKey: string;
}

export interface FunctionView {
  roleId?: string | null;
  scopeType?: string | null;
  scopeId?: string | null;
  status?: string | null;
}

const KIND_ORDER: readonly WorkspaceKind[] = [
  'RESEARCHER',
  'REVIEWER',
  'COMMITTEE_PRESIDENT',
  'EXECUTIVE_SECRETARY',
  'ADMINISTRATOR'
];

const COMMITTEE_KINDS = new Set<WorkspaceKind>(['COMMITTEE_PRESIDENT', 'EXECUTIVE_SECRETARY']);

const WORKSPACE_PATHS: Record<WorkspaceKind, ReadonlySet<string>> = {
  RESEARCHER: new Set(['/', '/proyectos', '/proyectos/nuevo', '/vencimientos']),
  REVIEWER: new Set(['/', '/revisiones', '/historial']),
  COMMITTEE_PRESIDENT: new Set(['/', '/proyectos', '/comites', '/sesiones', '/revisores', '/vencimientos']),
  EXECUTIVE_SECRETARY: new Set(['/', '/proyectos', '/comites', '/sesiones', '/revisores', '/vencimientos']),
  ADMINISTRATOR: new Set([
    '/',
    '/solicitudes',
    '/proyectos',
    '/comites',
    '/sesiones',
    '/revisores',
    '/vencimientos',
    '/usuarios',
    '/configuracion',
    '/auditoria'
  ])
};

export function workspaceAllows(kind: WorkspaceKind | null | undefined, path: string): boolean {
  if (!kind) {
    return true;
  }
  return WORKSPACE_PATHS[kind].has(path);
}

export function buildWorkspaces(views: readonly FunctionView[], reviewerCapability = false): EthicsWorkspace[] {
  let researcher = false;
  let reviewer = false;
  let administrator = false;
  const presidents = new Set<string>();
  const secretaries = new Set<string>();
  let presidentOpen = false;
  let secretaryOpen = false;

  for (const view of views) {
    const role = view.roleId ?? '';
    const status = view.status ?? 'ACTIVE';
    if (role === 'REVIEWER') {
      if (status === 'ACTIVE' || status === 'COMPLETED') {
        reviewer = true;
      }
      continue;
    }
    if (status !== 'ACTIVE') {
      continue;
    }
    if (role === 'RESEARCHER') {
      researcher = true;
    } else if (role === 'ADMINISTRATOR') {
      administrator = true;
    } else if (role === 'COMMITTEE_PRESIDENT') {
      const scope = committeeScope(view);
      if (scope) {
        presidents.add(scope);
      } else {
        presidentOpen = true;
      }
    } else if (role === 'EXECUTIVE_SECRETARY') {
      const scope = committeeScope(view);
      if (scope) {
        secretaries.add(scope);
      } else {
        secretaryOpen = true;
      }
    }
  }
  if (reviewerCapability) {
    reviewer = true;
  }

  const items: EthicsWorkspace[] = [];
  if (researcher) {
    items.push(plain('RESEARCHER', 'workspace.researcher', 'workspace.researcherHint'));
  }
  if (reviewer) {
    items.push(plain('REVIEWER', 'workspace.reviewer', 'workspace.reviewerHint'));
  }
  pushCommittees(items, 'COMMITTEE_PRESIDENT', presidents, presidentOpen, 'workspace.president', 'workspace.presidentPlain', 'workspace.presidentHint');
  pushCommittees(items, 'EXECUTIVE_SECRETARY', secretaries, secretaryOpen, 'workspace.secretary', 'workspace.secretaryPlain', 'workspace.secretaryHint');
  if (administrator) {
    items.push(plain('ADMINISTRATOR', 'workspace.administrator', 'workspace.administratorHint'));
  }
  return items;
}

export function chooseWorkspace(items: readonly EthicsWorkspace[], storedId: string | null): EthicsWorkspace | null {
  const stored = storedId ? items.find((item) => item.id === storedId) : undefined;
  if (stored) {
    return stored;
  }
  for (const kind of KIND_ORDER) {
    const match = items.find((item) => item.kind === kind);
    if (match) {
      return match;
    }
  }
  return null;
}

function plain(kind: WorkspaceKind, labelKey: string, descriptionKey: string): EthicsWorkspace {
  return { id: kind, kind, labelKey, descriptionKey };
}

function pushCommittees(
  items: EthicsWorkspace[],
  kind: 'COMMITTEE_PRESIDENT' | 'EXECUTIVE_SECRETARY',
  scopes: ReadonlySet<string>,
  open: boolean,
  scopedKey: string,
  plainKey: string,
  descriptionKey: string
): void {
  const ordered = [...scopes].sort((a, b) => a.localeCompare(b));
  if (ordered.length) {
    for (const scopeId of ordered) {
      items.push({
        id: `${kind}:${scopeId}`,
        kind,
        scopeType: 'COMMITTEE',
        scopeId,
        labelKey: scopedKey,
        descriptionKey
      });
    }
    return;
  }
  if (open) {
    items.push({ id: kind, kind, labelKey: plainKey, descriptionKey });
  }
}

function committeeScope(view: FunctionView): string | null {
  if (view.scopeType !== 'COMMITTEE') {
    return null;
  }
  const scope = view.scopeId?.trim();
  if (!scope || scope === '*') {
    return null;
  }
  return scope;
}

export function isCommitteeWorkspace(kind: WorkspaceKind | null | undefined): boolean {
  return !!kind && COMMITTEE_KINDS.has(kind);
}
