import { NEW_PROJECT_FORM } from '../core/forms/new-project-form.definition';
import { VALIDATION_FORM } from '../core/forms/validation-project-form.definition';
import { optionLabels, optionsOf } from '../core/forms/form.types';
import { PROTOTYPE_RULE, requiresTutor } from '../core/forms/form-rules';

export { PROTOTYPE_RULE, requiresTutor };

export const FUNDING_SOURCES = optionLabels(NEW_PROJECT_FORM, 'NP-002');
export const FUNDING_STATUSES = optionsOf(NEW_PROJECT_FORM, 'NP-003').map((item) => ({
  value: item.id,
  labelKey: item.labelKey ?? `forms.newProject.questions.NP003.options.${item.id}`
}));
export const CAMPUSES = optionLabels(NEW_PROJECT_FORM, 'NP-007');
export const FACULTIES = optionLabels(NEW_PROJECT_FORM, 'NP-008');
export const IR_POSITIONS = optionLabels(NEW_PROJECT_FORM, 'NP-010');
export const SPONSOR_POSITIONS = optionLabels(NEW_PROJECT_FORM, 'NP-016');
export const VALIDATION_IR_CATEGORIES = optionLabels(VALIDATION_FORM, 'VAL-004');
export const PROPOSAL_FRAMES = optionLabels(NEW_PROJECT_FORM, 'NP-017');
export const ACTIVITY_TYPES = optionLabels(NEW_PROJECT_FORM, 'NP-018');
export const ACTIVITY_FRAMES = optionLabels(NEW_PROJECT_FORM, 'NP-019');
export const TEAM_ROLES = optionLabels(NEW_PROJECT_FORM, 'NP-022');
export const EXPECTED_PRODUCTS = optionLabels(NEW_PROJECT_FORM, 'NP-023');
export const VALIDATION_SUPPORT_DOCS = optionsOf(VALIDATION_FORM, 'VAL-018').map((item) => ({
  key: item.id,
  labelKey: item.labelKey ?? `forms.validation.questions.VAL018.options.${item.id}`,
  required: !!item.required
}));

export const DECISION_CATALOG = [
  'RECEIVED',
  'IN_REVIEW',
  'WITH_OBSERVATIONS',
  'AWAITING_IR',
  'APPROVED',
  'REJECTED',
  'WITHDRAWN',
  'EXEMPT',
  'TO_BE_DETERMINED',
  'INVALID',
  'PENDING'
] as const;

export const ROLE_LABELS: Record<string, string> = {
  ADMINISTRATOR: 'catalogs.roles.ADMINISTRATOR',
  COMMITTEE_PRESIDENT: 'catalogs.roles.COMMITTEE_PRESIDENT',
  EXECUTIVE_SECRETARY: 'catalogs.roles.EXECUTIVE_SECRETARY',
  COMMITTEE_MEMBER: 'catalogs.roles.COMMITTEE_MEMBER',
  RESEARCHER: 'catalogs.roles.RESEARCHER'
};

export const OFFICIAL_COMMITTEE_NAMES: Record<string, string> = {
  CEC: 'Comité Ético Científico',
  CECUA: 'Comité Institucional de Ética en Cuidado y Uso de Animales',
  BIOSEGURIDAD: 'Comité de Bioseguridad',
  NO_REQUIERE_COMITE: 'No requiere comité'
};

export const COMMITTEE_LABELS: Record<string, string> = {
  CEC: 'catalogs.committees.CEC',
  CECUA: 'catalogs.committees.CECUA',
  BIOSEGURIDAD: 'catalogs.committees.BIOSEGURIDAD',
  NO_REQUIERE_COMITE: 'catalogs.committees.NO_REQUIERE_COMITE'
};

export const CASE_STATUS_LABELS: Record<string, string> = {
  DRAFT: 'catalogs.statuses.DRAFT',
  PENDING_CLASSIFICATION: 'catalogs.statuses.PENDING_CLASSIFICATION',
  IN_EVALUATION: 'catalogs.statuses.IN_EVALUATION',
  MIXED: 'catalogs.statuses.MIXED',
  PENDING_DOCUMENTS: 'catalogs.statuses.PENDING_DOCUMENTS',
  NOTIFIED: 'catalogs.statuses.NOTIFIED',
  ADMISSIBILITY: 'catalogs.statuses.ADMISSIBILITY',
  RETURNED: 'catalogs.statuses.RETURNED',
  PENDING_ASSIGNMENT: 'catalogs.statuses.PENDING_ASSIGNMENT',
  PENDING_ACCEPTANCE: 'catalogs.statuses.PENDING_ACCEPTANCE',
  IN_REVIEW: 'catalogs.statuses.IN_REVIEW',
  WITH_OBSERVATIONS: 'catalogs.statuses.WITH_OBSERVATIONS',
  AWAITING_RESEARCHER: 'catalogs.statuses.AWAITING_RESEARCHER',
  PENDING_SESSION: 'catalogs.statuses.PENDING_SESSION',
  PENDING_RESOLUTION: 'catalogs.statuses.PENDING_RESOLUTION',
  PENDING_PRESIDENT_VALIDATION: 'catalogs.statuses.PENDING_PRESIDENT_VALIDATION',
  APPROVED: 'catalogs.statuses.APPROVED',
  REJECTED: 'catalogs.statuses.REJECTED',
  WITHDRAWN: 'catalogs.statuses.WITHDRAWN',
  EXEMPT: 'catalogs.statuses.EXEMPT',
  TO_BE_DETERMINED: 'catalogs.statuses.TO_BE_DETERMINED',
  INVALID: 'catalogs.statuses.INVALID',
  PENDING: 'catalogs.statuses.PENDING',
  CLASSIFIED: 'catalogs.statuses.CLASSIFIED',
  SUBMITTED: 'catalogs.statuses.SUBMITTED',
  ACCEPTED: 'catalogs.assignmentStatus.ACCEPTED',
  CONFLICT: 'catalogs.assignmentStatus.CONFLICT',
  COMPLETED: 'catalogs.assignmentStatus.COMPLETED'
};

export const DECISION_LEGACY: Record<string, string> = {
  Recibido: 'RECEIVED',
  'En revisión': 'IN_REVIEW',
  'Con observaciones': 'WITH_OBSERVATIONS',
  'En espera respuesta IR': 'AWAITING_IR',
  Aprobado: 'APPROVED',
  Rechazado: 'REJECTED',
  Retirado: 'WITHDRAWN',
  Exento: 'EXEMPT',
  'Por determinar': 'TO_BE_DETERMINED',
  Inválido: 'INVALID',
  Pendiente: 'PENDING'
};

export function decisionCode(value: string): string {
  return DECISION_LEGACY[value] ?? value;
}

export function decisionLabelKey(value: string): string {
  return `catalogs.decisions.${decisionCode(value)}`;
}
