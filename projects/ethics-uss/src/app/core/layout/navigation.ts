export interface NavLink {
  path: string;
  labelKey: string;
  action?: boolean;
}

export interface NavSection {
  id: string;
  labelKey?: string;
  collapsible: boolean;
  items: NavLink[];
}

export const SIDEBAR_SECTIONS_KEY = 'ethics.sidebar.sections';

/** Default visual state by section id. It is not a role and not a permission. */
export const DEFAULT_OPEN_SECTIONS: Readonly<Record<string, boolean>> = {
  management: true,
  evaluation: true,
  myReviews: true,
  followUp: false,
  administration: true
};

interface NavRule {
  path: string;
  labelKey: string;
  action?: boolean;
  when: (capabilities: ReadonlySet<string>) => boolean;
}

const SECTIONS: readonly { id: string; labelKey?: string; items: readonly NavRule[] }[] = [
  {
    id: 'home',
    items: [
      { path: '/', labelKey: 'navigation.home', when: () => true }
    ]
  },
  {
    id: 'management',
    labelKey: 'navigation.sections.management',
    items: [
      {
        path: '/solicitudes',
        labelKey: 'navigation.applications',
        when: (capabilities) => capabilities.has('VIEW_PENDING_CLASSIFICATION') && capabilities.has('VIEW_CONFIGURATION')
      },
      { path: '/proyectos', labelKey: 'navigation.projects', when: (capabilities) => capabilities.has('VIEW_PROJECTS') },
      {
        path: '/proyectos/nuevo',
        labelKey: 'navigation.newApplication',
        action: true,
        when: (capabilities) => capabilities.has('CREATE_REQUEST')
      }
    ]
  },
  {
    id: 'evaluation',
    labelKey: 'navigation.sections.evaluation',
    items: [
      { path: '/comites', labelKey: 'navigation.committees', when: (capabilities) => capabilities.has('VIEW_CONFIGURATION') },
      { path: '/sesiones', labelKey: 'navigation.sessions', when: (capabilities) => capabilities.has('VIEW_COMMITTEE_SESSIONS') },
      { path: '/revisores', labelKey: 'navigation.reviewers', when: (capabilities) => capabilities.has('VIEW_REVIEWER_POOL') }
    ]
  },
  {
    id: 'myReviews',
    labelKey: 'navigation.sections.myReviews',
    items: [
      { path: '/revisiones', labelKey: 'navigation.assignedReviews', when: (capabilities) => capabilities.has('VIEW_OWN_REVIEW') },
      {
        path: '/historial',
        labelKey: 'navigation.history',
        when: (capabilities) => capabilities.has('VIEW_HISTORY') && capabilities.has('VIEW_OWN_REVIEW') && !capabilities.has('VIEW_REVIEWER_POOL')
      }
    ]
  },
  {
    id: 'followUp',
    labelKey: 'navigation.sections.followUp',
    items: [
      {
        path: '/vencimientos',
        labelKey: 'navigation.expirations',
        when: (capabilities) => capabilities.has('VIEW_EXPIRATIONS') && capabilities.has('VIEW_COMMITTEE_SESSIONS')
      }
    ]
  },
  {
    id: 'administration',
    labelKey: 'navigation.sections.administration',
    items: [
      { path: '/usuarios', labelKey: 'navigation.users', when: (capabilities) => capabilities.has('MANAGE_USERS') },
      { path: '/configuracion', labelKey: 'navigation.settings', when: (capabilities) => capabilities.has('VIEW_CONFIGURATION') },
      { path: '/auditoria', labelKey: 'navigation.audit', when: (capabilities) => capabilities.has('VIEW_AUDIT') }
    ]
  }
];

const HIDDEN_WITHOUT_ASSIGNMENT = new Set(['/proyectos', '/revisiones', '/historial']);

export function visibleNavigation(
  capabilities: ReadonlySet<string>,
  options?: { hideUnassignedCasePaths?: boolean }
): NavSection[] {
  const hide = options?.hideUnassignedCasePaths === true;
  return SECTIONS.map((section) => ({
    id: section.id,
    labelKey: section.labelKey,
    collapsible: section.labelKey != null,
    items: section.items
      .filter((item) => item.when(capabilities))
      .filter((item) => !(hide && HIDDEN_WITHOUT_ASSIGNMENT.has(item.path)))
      .map((item) => ({ path: item.path, labelKey: item.labelKey, ...(item.action ? { action: true } : {}) }))
  })).filter((section) => section.items.length > 0);
}

export function parseSidebarSections(raw: string | null): Record<string, boolean> | null {
  if (!raw) {
    return null;
  }
  try {
    const value = JSON.parse(raw) as unknown;
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return null;
    }
    const stored: Record<string, boolean> = {};
    for (const [key, item] of Object.entries(value)) {
      if (typeof item === 'boolean') {
        stored[key] = item;
      }
    }
    return stored;
  } catch {
    return null;
  }
}

export function sectionIdForUrl(sections: readonly NavSection[], url: string): string | null {
  const path = url.split('?')[0].split('#')[0];
  let match: { id: string; length: number } | null = null;
  for (const section of sections) {
    if (!section.collapsible) {
      continue;
    }
    for (const item of section.items) {
      const hit = path === item.path || (item.path !== '/' && path.startsWith(`${item.path}/`));
      if (hit && (match === null || item.path.length > match.length)) {
        match = { id: section.id, length: item.path.length };
      }
    }
  }
  return match?.id ?? null;
}

export function resolveOpenSections(
  sections: readonly NavSection[],
  url: string,
  stored: Record<string, boolean> | null
): Record<string, boolean> {
  const active = sectionIdForUrl(sections, url);
  const open: Record<string, boolean> = {};
  for (const section of sections) {
    if (!section.collapsible) {
      continue;
    }
    const fallback = DEFAULT_OPEN_SECTIONS[section.id] ?? true;
    const preferred = stored && typeof stored[section.id] === 'boolean' ? stored[section.id] : fallback;
    open[section.id] = preferred || section.id === active;
  }
  return open;
}

export function toggleStoredSection(
  stored: Record<string, boolean> | null,
  sections: readonly NavSection[],
  id: string,
  url: string
): Record<string, boolean> | null {
  const section = sections.find((item) => item.id === id && item.collapsible);
  if (!section || section.id === sectionIdForUrl(sections, url)) {
    return stored;
  }
  const current = resolveOpenSections(sections, url, stored);
  const next: Record<string, boolean> = { ...(stored ?? {}) };
  for (const item of sections) {
    if (item.collapsible && typeof next[item.id] !== 'boolean') {
      next[item.id] = DEFAULT_OPEN_SECTIONS[item.id] ?? true;
    }
  }
  next[id] = current[id] === false;
  return next;
}
