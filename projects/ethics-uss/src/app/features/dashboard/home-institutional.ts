/**
 * Módulos del inicio institucional.
 * Las capabilities son las de permissions-V4.yml. El nombre del rol no entra aquí.
 * El prototipo aún no llama a GET /api/v1/ethics/permissions; este catálogo
 * refleja ese archivo para poder componer el Home.
 */
export const YAML_CAPABILITIES = {
  ADMINISTRATOR: [
    'HOME_VIEW_PROJECT_SUMMARY',
    'HOME_VIEW_ATTENTION_QUEUE',
    'HOME_VIEW_PENDING_ADMISSIBILITY',
    'HOME_VIEW_PENDING_REVIEWER_ASSIGNMENT',
    'HOME_VIEW_REVIEWS_TO_ACCEPT',
    'HOME_VIEW_REVIEWS_TO_COMPLETE',
    'HOME_VIEW_PENDING_OBSERVATION_CONSOLIDATION',
    'HOME_VIEW_RESOLUTIONS_TO_PREPARE',
    'HOME_VIEW_RESOLUTIONS_TO_VALIDATE',
    'HOME_VIEW_EXPIRATIONS',
    'HOME_VIEW_RECENT_ACTIVITY',
    'VIEW_PROJECTS',
    'VIEW_NOTIFICATIONS',
    'VIEW_EXPIRATIONS',
    'VIEW_CONFIGURATION',
    'VIEW_AUDIT',
    'MANAGE_USERS',
    'VIEW_COMMITTEE_SESSIONS',
    'VIEW_REVIEWER_POOL',
    'VIEW_DRAFT_REQUESTS',
    'SUBMIT_REQUEST',
    'VIEW_PENDING_CLASSIFICATION',
    'VIEW_IN_REVIEW',
    'VIEW_ADMISSIBILITY',
    'VIEW_PENDING_OBSERVATIONS_SEND',
    'VIEW_SAVED_CONSOLIDATED_OBSERVATIONS',
    'VIEW_PUBLISHED_OBSERVATIONS',
    'VIEW_RESEARCHER_RESPONSE',
    'VIEW_PENDING_RESOLUTION',
    'VIEW_RESOLUTION',
    'VIEW_EXPIRING_SOON',
    'VIEW_VALID_EXPIRED',
    'VIEW_CLOSED_ARCHIVED',
    'HISTORY_VIEW_CLASSIFICATION',
    'HISTORY_VIEW_DOCUMENT_UPLOAD',
    'HISTORY_VIEW_COMMITTEE_NOTIFICATION',
    'HISTORY_VIEW_REVIEWER_ASSIGNMENT',
    'HISTORY_VIEW_ASSIGNMENT_ACCEPTANCE',
    'HISTORY_VIEW_SEND_TO_REVIEW',
    'HISTORY_VIEW_OBSERVATIONS_PUBLISHED',
    'HISTORY_VIEW_RESEARCHER_RESPONSE',
    'HISTORY_VIEW_SEND_TO_VALIDATION',
    'HISTORY_VIEW_RETURN_TO_SECRETARY',
    'HISTORY_VIEW_RESOLUTION_VALIDATION',
    'HISTORY_VIEW_RESOLUTION_DISPATCH',
    'VIEW_PROJECT_SUMMARY',
    'VIEW_COMMITTEE_WORKSPACE',
    'VIEW_DOCUMENTS',
    'DOWNLOAD_DOCUMENTS',
    'UPLOAD_DOCUMENTS',
    'UPLOAD_DOCUMENT_VERSION',
    'VIEW_DOCUMENT_VERSIONS',
    'VIEW_REVIEWER_ASSIGNMENTS',
    'ASSIGN_REVIEWERS',
    'REASSIGN_REVIEWER',
    'VIEW_ALL_REVIEWS',
    'CONSOLIDATE_OBSERVATIONS',
    'EDIT_CONSOLIDATED_OBSERVATIONS',
    'SAVE_CONSOLIDATED_OBSERVATIONS',
    'PUBLISH_OBSERVATIONS',
    'PREPARE_RESOLUTION',
    'VALIDATE_RESOLUTION',
    'RETURN_RESOLUTION',
    'VIEW_POST_APPROVAL_PROCESSES',
    'VIEW_HISTORY',
    'SUBMIT_OWN_REVIEW'
  ],
  COMMITTEE_PRESIDENT: [
    'HOME_VIEW_PROJECT_SUMMARY',
    'HOME_VIEW_ATTENTION_QUEUE',
    'HOME_VIEW_PENDING_ADMISSIBILITY',
    'HOME_VIEW_PENDING_REVIEWER_ASSIGNMENT',
    'HOME_VIEW_REVIEWS_TO_ACCEPT',
    'HOME_VIEW_REVIEWS_TO_COMPLETE',
    'HOME_VIEW_PENDING_OBSERVATION_CONSOLIDATION',
    'HOME_VIEW_RESOLUTIONS_TO_PREPARE',
    'HOME_VIEW_RESOLUTIONS_TO_VALIDATE',
    'HOME_VIEW_EXPIRATIONS',
    'HOME_VIEW_RECENT_ACTIVITY',
    'VIEW_PROJECTS',
    'VIEW_NOTIFICATIONS',
    'VIEW_EXPIRATIONS',
    'VIEW_COMMITTEE_SESSIONS',
    'VIEW_REVIEWER_POOL',
    'VIEW_IN_REVIEW',
    'VIEW_ADMISSIBILITY',
    'VIEW_PENDING_OBSERVATIONS_SEND',
    'VIEW_SAVED_CONSOLIDATED_OBSERVATIONS',
    'VIEW_PUBLISHED_OBSERVATIONS',
    'VIEW_RESEARCHER_RESPONSE',
    'VIEW_PENDING_RESOLUTION',
    'VIEW_RESOLUTION',
    'VIEW_EXPIRING_SOON',
    'VIEW_VALID_EXPIRED',
    'VIEW_CLOSED_ARCHIVED',
    'HISTORY_VIEW_CLASSIFICATION',
    'HISTORY_VIEW_DOCUMENT_UPLOAD',
    'HISTORY_VIEW_COMMITTEE_NOTIFICATION',
    'HISTORY_VIEW_REVIEWER_ASSIGNMENT',
    'HISTORY_VIEW_ASSIGNMENT_ACCEPTANCE',
    'HISTORY_VIEW_SEND_TO_REVIEW',
    'HISTORY_VIEW_OBSERVATIONS_PUBLISHED',
    'HISTORY_VIEW_RESEARCHER_RESPONSE',
    'HISTORY_VIEW_SEND_TO_VALIDATION',
    'HISTORY_VIEW_RETURN_TO_SECRETARY',
    'HISTORY_VIEW_RESOLUTION_VALIDATION',
    'HISTORY_VIEW_RESOLUTION_DISPATCH',
    'VIEW_PROJECT_SUMMARY',
    'VIEW_COMMITTEE_WORKSPACE',
    'VIEW_DOCUMENTS',
    'DOWNLOAD_DOCUMENTS',
    'VIEW_DOCUMENT_VERSIONS',
    'VIEW_REVIEWER_ASSIGNMENTS',
    'ASSIGN_REVIEWERS',
    'REASSIGN_REVIEWER',
    'REASSIGN_REVIEWER_ADVANCED_STAGE',
    'VIEW_ALL_REVIEWS',
    'CONSOLIDATE_OBSERVATIONS',
    'EDIT_CONSOLIDATED_OBSERVATIONS',
    'SAVE_CONSOLIDATED_OBSERVATIONS',
    'PUBLISH_OBSERVATIONS',
    'PREPARE_RESOLUTION',
    'VALIDATE_RESOLUTION',
    'RETURN_RESOLUTION',
    'VIEW_POST_APPROVAL_PROCESSES',
    'VIEW_HISTORY'
  ],
  EXECUTIVE_SECRETARY: [
    'HOME_VIEW_PROJECT_SUMMARY',
    'HOME_VIEW_ATTENTION_QUEUE',
    'HOME_VIEW_PENDING_ADMISSIBILITY',
    'HOME_VIEW_PENDING_REVIEWER_ASSIGNMENT',
    'HOME_VIEW_REVIEWS_TO_ACCEPT',
    'HOME_VIEW_REVIEWS_TO_COMPLETE',
    'HOME_VIEW_PENDING_OBSERVATION_CONSOLIDATION',
    'HOME_VIEW_RESOLUTIONS_TO_PREPARE',
    'HOME_VIEW_RESOLUTIONS_TO_VALIDATE',
    'HOME_VIEW_EXPIRATIONS',
    'HOME_VIEW_RECENT_ACTIVITY',
    'VIEW_PROJECTS',
    'VIEW_NOTIFICATIONS',
    'VIEW_EXPIRATIONS',
    'VIEW_COMMITTEE_SESSIONS',
    'VIEW_REVIEWER_POOL',
    'VIEW_IN_REVIEW',
    'VIEW_ADMISSIBILITY',
    'VIEW_PENDING_OBSERVATIONS_SEND',
    'VIEW_SAVED_CONSOLIDATED_OBSERVATIONS',
    'VIEW_PUBLISHED_OBSERVATIONS',
    'VIEW_RESEARCHER_RESPONSE',
    'VIEW_PENDING_RESOLUTION',
    'VIEW_RESOLUTION',
    'VIEW_EXPIRING_SOON',
    'VIEW_VALID_EXPIRED',
    'VIEW_CLOSED_ARCHIVED',
    'HISTORY_VIEW_CLASSIFICATION',
    'HISTORY_VIEW_DOCUMENT_UPLOAD',
    'HISTORY_VIEW_COMMITTEE_NOTIFICATION',
    'HISTORY_VIEW_REVIEWER_ASSIGNMENT',
    'HISTORY_VIEW_ASSIGNMENT_ACCEPTANCE',
    'HISTORY_VIEW_SEND_TO_REVIEW',
    'HISTORY_VIEW_OBSERVATIONS_PUBLISHED',
    'HISTORY_VIEW_RESEARCHER_RESPONSE',
    'HISTORY_VIEW_SEND_TO_VALIDATION',
    'HISTORY_VIEW_RETURN_TO_SECRETARY',
    'HISTORY_VIEW_RESOLUTION_VALIDATION',
    'HISTORY_VIEW_RESOLUTION_DISPATCH',
    'VIEW_PROJECT_SUMMARY',
    'VIEW_COMMITTEE_WORKSPACE',
    'VIEW_DOCUMENTS',
    'DOWNLOAD_DOCUMENTS',
    'VIEW_DOCUMENT_VERSIONS',
    'VIEW_REVIEWER_ASSIGNMENTS',
    'ASSIGN_REVIEWERS',
    'REASSIGN_REVIEWER',
    'VIEW_ALL_REVIEWS',
    'CONSOLIDATE_OBSERVATIONS',
    'EDIT_CONSOLIDATED_OBSERVATIONS',
    'SAVE_CONSOLIDATED_OBSERVATIONS',
    'PUBLISH_OBSERVATIONS',
    'PREPARE_RESOLUTION',
    'VIEW_POST_APPROVAL_PROCESSES',
    'VIEW_HISTORY'
  ],
  REVIEWER: [
    'HOME_VIEW_PROJECT_SUMMARY',
    'HOME_VIEW_ATTENTION_QUEUE',
    'HOME_VIEW_REVIEWS_TO_ACCEPT',
    'HOME_VIEW_REVIEWS_TO_COMPLETE',
    'HOME_VIEW_RECENT_ACTIVITY',
    'VIEW_PROJECTS',
    'VIEW_NOTIFICATIONS',
    'VIEW_IN_REVIEW',
    'VIEW_PUBLISHED_OBSERVATIONS',
    'VIEW_RESEARCHER_RESPONSE',
    'HISTORY_VIEW_REVIEWER_ASSIGNMENT',
    'HISTORY_VIEW_ASSIGNMENT_ACCEPTANCE',
    'HISTORY_VIEW_SEND_TO_REVIEW',
    'HISTORY_VIEW_OBSERVATIONS_PUBLISHED',
    'HISTORY_VIEW_RESEARCHER_RESPONSE',
    'VIEW_PROJECT_SUMMARY',
    'VIEW_COMMITTEE_WORKSPACE',
    'VIEW_DOCUMENTS',
    'DOWNLOAD_DOCUMENTS',
    'VIEW_DOCUMENT_VERSIONS',
    'VIEW_HISTORY',
    'ACCEPT_REVIEW_ASSIGNMENT',
    'REJECT_REVIEW_ASSIGNMENT',
    'VIEW_OWN_REVIEW',
    'EDIT_OWN_REVIEW',
    'SUBMIT_OWN_REVIEW'
  ],
  RESEARCHER: [
    'HOME_VIEW_PROJECT_SUMMARY',
    'HOME_VIEW_ATTENTION_QUEUE',
    'HOME_VIEW_EXPIRATIONS',
    'HOME_VIEW_RECENT_ACTIVITY',
    'VIEW_PROJECTS',
    'CREATE_REQUEST',
    'VIEW_NOTIFICATIONS',
    'VIEW_EXPIRATIONS',
    'VIEW_DRAFT_REQUESTS',
    'SUBMIT_REQUEST',
    'VIEW_PENDING_CLASSIFICATION',
    'VIEW_IN_REVIEW',
    'VIEW_PUBLISHED_OBSERVATIONS',
    'VIEW_RESEARCHER_RESPONSE',
    'VIEW_RESOLUTION',
    'VIEW_EXPIRING_SOON',
    'VIEW_VALID_EXPIRED',
    'VIEW_CLOSED_ARCHIVED',
    'HISTORY_VIEW_CLASSIFICATION',
    'HISTORY_VIEW_DOCUMENT_UPLOAD',
    'HISTORY_VIEW_COMMITTEE_NOTIFICATION',
    'HISTORY_VIEW_OBSERVATIONS_PUBLISHED',
    'HISTORY_VIEW_RESEARCHER_RESPONSE',
    'HISTORY_VIEW_RESOLUTION_VALIDATION',
    'HISTORY_VIEW_RESOLUTION_DISPATCH',
    'VIEW_PROJECT_SUMMARY',
    'VIEW_COMMITTEE_WORKSPACE',
    'VIEW_DOCUMENTS',
    'DOWNLOAD_DOCUMENTS',
    'UPLOAD_DOCUMENTS',
    'UPLOAD_DOCUMENT_VERSION',
    'VIEW_DOCUMENT_VERSIONS',
    'RESPOND_TO_OBSERVATIONS',
    'VIEW_POST_APPROVAL_PROCESSES',
    'VIEW_HISTORY',
    'START_AMENDMENT',
    'START_FOLLOW_UP',
    'START_RENEWAL'
  ],
};

export type InstitutionalQueue = 'classification' | 'admissibility' | 'resolutions' | 'expiring';

export interface InstitutionalModule {
  id: string;
  labelKey: string;
  ariaKey: string;
  capability: string;
  route: string;
  query: Record<string, string>;
  priority: number;
  queue: InstitutionalQueue;
}

/**
 * Cada módulo cita la capability que permissions-V4.yml concede.
 * No se recuperan recortes del conjunto antiguo.
 */
export const INSTITUTIONAL_MODULES: readonly InstitutionalModule[] = [
  {
    id: 'pending-classification',
    labelKey: 'dashboard.pendingClassification',
    ariaKey: 'dashboard.viewPendingClassification',
    capability: 'VIEW_PENDING_CLASSIFICATION',
    route: '/solicitudes',
    query: {},
    priority: 10,
    queue: 'classification'
  },
  {
    id: 'admissibility',
    labelKey: 'dashboard.admissibility',
    ariaKey: 'dashboard.viewAdmissibility',
    capability: 'VIEW_ADMISSIBILITY',
    route: '/expedientes',
    query: { queue: 'admissibility' },
    priority: 20,
    queue: 'admissibility'
  },
  {
    id: 'resolutions',
    labelKey: 'dashboard.resolutions',
    ariaKey: 'dashboard.viewResolutions',
    capability: 'VIEW_RESOLUTION',
    route: '/expedientes',
    query: { queue: 'resolutions' },
    priority: 30,
    queue: 'resolutions'
  },
  {
    id: 'expiring',
    labelKey: 'dashboard.expiring',
    ariaKey: 'dashboard.viewExpiring',
    capability: 'VIEW_EXPIRATIONS',
    route: '/vencimientos',
    query: { semaphore: 'PROXIMO' },
    priority: 40,
    queue: 'expiring'
  }
];

export function capabilitiesForRoles(roles: readonly string[]): Set<string> {
  const granted = new Set<string>();
  for (const role of roles) {
    for (const capability of YAML_CAPABILITIES[role as keyof typeof YAML_CAPABILITIES] ?? []) {
      granted.add(capability);
    }
  }
  return granted;
}

export function institutionalModules(capabilities: ReadonlySet<string>): InstitutionalModule[] {
  const seen = new Set<string>();
  return INSTITUTIONAL_MODULES.filter((module) => capabilities.has(module.capability))
    .filter((module) => {
      if (seen.has(module.id)) {
        return false;
      }
      seen.add(module.id);
      return true;
    })
    .sort((a, b) => a.priority - b.priority);
}

export type CaseQueue = 'admissibility' | 'resolutions';

export function caseQueue(value: string | null | undefined): CaseQueue | '' {
  return value === 'admissibility' || value === 'resolutions' ? value : '';
}

export function matchesCaseQueue(status: string, queue: CaseQueue | ''): boolean {
  if (!queue) {
    return true;
  }
  if (queue === 'admissibility') {
    return status === 'ADMISSIBILITY';
  }
  return status === 'PENDING_RESOLUTION' || status === 'PENDING_PRESIDENT_VALIDATION';
}

export function expirySemaphore(value: string | null | undefined): 'PROXIMO' | 'VIGENTE' | 'VENCIDO' | '' {
  return value === 'PROXIMO' || value === 'VIGENTE' || value === 'VENCIDO' ? value : '';
}

export function attentionForCapability(
  status: string,
  semaphore: string | null | undefined,
  capabilities: ReadonlySet<string>
): { reasonKey: string; priority: number } | null {
  if (capabilities.has('VIEW_ADMISSIBILITY') && status === 'ADMISSIBILITY') {
    return { reasonKey: 'dashboard.reviewAdmissibility', priority: 10 };
  }
  if (capabilities.has('VIEW_RESOLUTION') && matchesCaseQueue(status, 'resolutions')) {
    return { reasonKey: 'dashboard.pendingResolution', priority: 20 };
  }
  if (capabilities.has('VIEW_EXPIRATIONS') && semaphore === 'PROXIMO') {
    return { reasonKey: 'dashboard.expiring', priority: 30 };
  }
  return null;
}
