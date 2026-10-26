export type Role =
  | 'ADMINISTRATOR'
  | 'COMMITTEE_PRESIDENT'
  | 'EXECUTIVE_SECRETARY'
  | 'COMMITTEE_MEMBER'
  | 'RESEARCHER';

export type EthicsCommittee = 'CEC' | 'CECUA' | 'BIOSEGURIDAD';

export type ClassificationCode = EthicsCommittee | 'NO_REQUIERE_COMITE';

export type EthicsSubmissionType = 'NEW_PROJECT' | 'VALIDATION';

export type FundingStatus = 'AWARDED' | 'APPLYING' | 'NOT_APPLICABLE';

export type GlobalProjectStatus =
  | 'DRAFT'
  | 'PENDING_CLASSIFICATION'
  | 'IN_EVALUATION'
  | 'WITH_OBSERVATIONS'
  | 'APPROVED'
  | 'MIXED'
  | 'EXEMPT'
  | 'REJECTED';

export type CommitteeCaseStatus =
  | 'PENDING_DOCUMENTS'
  | 'NOTIFIED'
  | 'ADMISSIBILITY'
  | 'RETURNED'
  | 'PENDING_ASSIGNMENT'
  | 'PENDING_ACCEPTANCE'
  | 'IN_REVIEW'
  | 'WITH_OBSERVATIONS'
  | 'AWAITING_RESEARCHER'
  | 'PENDING_SESSION'
  | 'PENDING_RESOLUTION'
  | 'PENDING_PRESIDENT_VALIDATION'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'EXEMPT'
  | 'TO_BE_DETERMINED'
  | 'INVALID'
  | 'PENDING';

export type AssignmentStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CONFLICT' | 'COMPLETED';

export type ObservationStatus = 'DRAFT' | 'PUBLISHED' | 'PENDING_RESPONSE' | 'ANSWERED';

export type SessionType = 'ORDINARY' | 'EXTRAORDINARY';

export type SessionStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'CLOSED';

export type ResolutionKind = 'APPROVED' | 'REJECTED' | 'EXEMPT' | 'WITHDRAWN' | 'TO_BE_DETERMINED';

export type ResolutionWorkflow = 'DRAFT' | 'PENDING_PRESIDENT' | 'VALIDATED' | 'ISSUED';

export type ApprovalSemaphore = 'VIGENTE' | 'PROXIMO' | 'VENCIDO';

export type PrivacyLevel = 'COMMITTEE_ONLY' | 'RESEARCHER_VISIBLE' | 'PUBLICABLE';

export type DocumentKind = 'REQUIRED' | 'SUPPORT' | 'RESPONSE' | 'RESOLUTION' | 'CERTIFICATE' | 'OBSERVATION';

export type ProcessKind = 'INITIAL' | 'AMENDMENT' | 'FOLLOW_UP' | 'CLOSURE_FOLLOW_UP';

export type ProcessStatus = 'DRAFT' | 'SUBMITTED' | 'RESOLVED';

export type PrototypeMark = 'NONE' | 'PROTOTYPE';

export type ReviewFormDefinitionId = 'CEC_REVIEW_V1' | 'CECUA_REVIEW_V1' | 'BIOSECURITY_REVIEW_V1';

export interface User {
  id: string;
  displayName: string;
  email: string;
  role: Role;
  committees: EthicsCommittee[];
  specialty?: string;
  activeLoad?: number;
  availability?: 'ALTA' | 'MEDIA' | 'BAJA';
  knownConflicts?: string[];
}

export interface Project {
  id: string;
  code: string;
  title: string;
  fundingSource: string;
  fundingStatus?: FundingStatus;
  fundingCode?: string;
  researcherId: string;
  type: EthicsSubmissionType;
  createdAt: string;
  updatedAt: string;
}

export interface EthicsClassification {
  automatic: ClassificationCode[];
  validated: ClassificationCode[] | null;
  justification?: string;
  validatedBy?: string;
  validatedAt?: string;
}

export interface EthicsSubmission {
  id: string;
  projectId: string;
  type: EthicsSubmissionType;
  status: 'DRAFT' | 'SUBMITTED' | 'CLASSIFIED';
  formVersion?: 'NEW_PROJECT_V1' | 'VALIDATION_V1';
  /** Respuestas canónicas por QuestionId. El payload plano se conserva como adapter. */
  answers?: Record<string, unknown>;
  payload: Record<string, unknown>;
  classification: EthicsClassification;
  submittedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentMetadata {
  id: string;
  ownerType: 'SUBMISSION' | 'CASE' | 'OBSERVATION' | 'RESOLUTION';
  ownerId: string;
  name: string;
  kind: DocumentKind;
  requirementKey?: string;
  /** Tipo documental (carta, acta, protocolo…). */
  documentType?: string;
  /** Comité al que pertenece el documento. Vacío en la carta transversal. */
  committeeType?: EthicsCommittee;
  sizeLabel: string;
  uploadedAt: string;
  version: number;
  processKind?: ProcessKind;
  /** Marca interna. No es formato institucional. */
  templateId?: 'PROTOTYPE_PDF_TEMPLATE';
  stampStatus?: PrototypeMark;
}

export interface CommitteeCase {
  id: string;
  code: string;
  projectId: string;
  submissionId: string;
  committee: EthicsCommittee;
  status: CommitteeCaseStatus;
  receivedAt: string;
  notifiedAt?: string;
  sessionId?: string;
  reviewerIds: string[];
  currentCycle: number;
  packageFrozen: boolean;
  resolutionId?: string;
  approvalIssuedAt?: string;
  approvalExpiresAt?: string;
  processKind?: ProcessKind;
  processStatus?: ProcessStatus;
}

export interface ReviewerAssignment {
  id: string;
  caseId: string;
  reviewerId: string;
  slot: 1 | 2;
  status: AssignmentStatus;
  assignedAt: string;
  decidedAt?: string;
  rejectReason?: string;
  conflictDeclared?: boolean;
  sessionId?: string;
}

export interface Review {
  id: string;
  assignmentId: string;
  caseId: string;
  reviewerId: string;
  cycleNumber: number;
  criteria: Record<string, string>;
  submittedAt?: string;
  privacy: PrivacyLevel;
  formDefinitionId?: ReviewFormDefinitionId;
}

export interface ReviewCycle {
  id: string;
  caseId: string;
  cycleNumber: number;
  openedAt: string;
  closedAt?: string;
  status?: 'OPEN' | 'PUBLISHED';
  consolidatedAt?: string;
  consolidatedMethodology?: string;
  consolidatedObservations?: string;
  consolidatedRisks?: string;
  pdfDocumentId?: string;
}

export interface Observation {
  id: string;
  code: string;
  caseId: string;
  cycleNumber: number;
  topic: string;
  text: string;
  relatedDocuments: string[];
  status: ObservationStatus;
  privacy: PrivacyLevel;
  methodology?: string;
  risks?: string;
  pdfDocumentId?: string;
  notice?: string;
}

export interface ObservationResponse {
  id: string;
  observationId: string;
  text: string;
  documentIds: string[];
  answeredAt: string;
}

export interface CommitteeSession {
  id: string;
  committee: EthicsCommittee;
  date: string;
  time: string;
  type: SessionType;
  status: SessionStatus;
  caseIds: string[];
}

export interface Resolution {
  id: string;
  correlative: string;
  caseId: string;
  kind: ResolutionKind;
  decisionCatalog: string;
  text: string;
  notes?: string;
  validityNote: string;
  workflow: ResolutionWorkflow;
  preparedBy?: string;
  validatedBy?: string;
  issuedAt?: string;
  intakeDate: string;
  selectedDocumentIds?: string[];
  signatureStatus?: PrototypeMark;
  stampStatus?: PrototypeMark;
  processKind?: ProcessKind;
  returnedComment?: string;
}

export interface EthicsApproval {
  caseId: string;
  issuedAt: string;
  expiresAt: string;
  semaphore: ApprovalSemaphore;
  daysLeft: number;
}

export interface NotificationItem {
  id: string;
  userId: string;
  title: string;
  body: string;
  createdAt: string;
  read: boolean;
  link?: string;
  type?: string;
  params?: Record<string, string | number>;
}

export interface CommitteeCommunication {
  id: string;
  caseId: string;
  projectId: string;
  actorId: string;
  actorName: string;
  kind: 'COMMITTEE_CONTACT' | 'COMMITTEE_NOTIFICATION' | 'OBSERVATION_NOTICE' | 'AMENDMENT' | 'FOLLOW_UP';
  body: string;
  createdAt: string;
  emailSimulated: boolean;
}

export interface AuditEvent {
  id: string;
  actorId: string;
  actorName: string;
  role: Role;
  date: string;
  action: string;
  objectType: string;
  objectId: string;
  description: string;
  oldValue?: string;
  newValue?: string;
  reason?: string;
  projectId?: string;
  caseId?: string;
}

export interface StoreState {
  users: User[];
  projects: Project[];
  submissions: EthicsSubmission[];
  cases: CommitteeCase[];
  documents: DocumentMetadata[];
  assignments: ReviewerAssignment[];
  reviews: Review[];
  cycles: ReviewCycle[];
  observations: Observation[];
  responses: ObservationResponse[];
  sessions: CommitteeSession[];
  resolutions: Resolution[];
  notifications: NotificationItem[];
  communications: CommitteeCommunication[];
  audit: AuditEvent[];
  nextResolutionSeq: number;
}
