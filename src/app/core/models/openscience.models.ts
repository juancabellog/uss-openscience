export type UserRole = string;
export type AppModuleCode =
  | 'DASHBOARD'
  | 'PEOPLE'
  | 'RESEARCH_OUTPUTS'
  | 'JOURNALS'
  | 'PROJECTS'
  | 'ORGANIZATIONS'
  | 'DIRECTORY'
  | 'EVENTS'
  | 'DATA_QUALITY'
  | 'INDICATORS'
  | 'INTEGRATIONS'
  | 'AUDIT'
  | 'ADMINISTRATION';
export type AppPermissionCode = 'CONSULT' | 'ADMINISTER';
export type UserStatus = 'PENDING_ACTIVATION' | 'ACTIVE' | 'DISABLED' | 'LOCKED';

export interface AuthPermission {
  module: AppModuleCode | string;
  permission: AppPermissionCode | string;
}

export interface CurrentUser {
  id: string;
  displayName: string;
  firstName?: string;
  lastName?: string;
  email: string;
  role?: UserRole;
  roles: string[];
  status?: UserStatus | string;
  preferredLanguage?: string;
  position?: string;
  effectivePermissions: AuthPermission[];
}

export interface AdminUserSummary {
  id: number;
  firstName: string;
  lastName: string;
  displayName: string;
  email: string;
  status: UserStatus | string;
  preferredLanguage: string;
  roles: string[];
  roleIds: number[];
  lastLoginAt: string | null;
  createdAt: string | null;
  deletedAt?: string | null;
  emailSent?: boolean;
}

export interface AdminRole {
  id: number;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  systemRole: boolean;
  userCount: number;
  permissions: AuthPermission[];
}

export interface AdminUserQuery {
  search?: string;
  status?: string;
  roleId?: number;
  sort?: 'name' | 'email' | 'status' | 'lastLoginAt' | 'createdAt';
  direction?: 'asc' | 'desc';
  page?: number;
  pageSize?: number;
}

export type DataQualityStatus = 'ok' | 'review' | 'pending';

export interface ScopusAffiliationRef {
  affiliationId: string;
  affiliationName: string;
}

export interface Researcher {
  id: number;
  uuid: string;
  firstName: string;
  lastName: string;
  email: string;
  position?: string;
  faculty?: string;
  orcid?: string;
  orcidVerified?: boolean;
  primaryId?: string;
  scopusIds: string[];
  scopusAffiliations: ScopusAffiliationRef[];
  publicationCount: number | null;
  dataQualityStatus?: DataQualityStatus;
}

export interface ResearcherDetail extends Researcher {
  publications: ResearchOutputSummary[];
  qualityIssueIds: string[];
  auditEntryIds: string[];
}

export interface ResearchOutputSummary {
  id: string;
  title: string;
  year: number | null;
  type: string;
  doi?: string;
  journal?: string;
  contributorCount: number | null;
  metadataStatus?: DataQualityStatus;
}

export interface ResearchOutput extends ResearchOutputSummary {
  uuid: string;
  contributors: string[];
}

export type DataQualityIssueStatus = 'open' | 'in_review' | 'validated' | 'corrected' | 'dismissed';

export interface DataQualityIssue {
  id: string;
  code: string;
  rule: string;
  entityType: 'person' | 'identifier' | 'output';
  entityLabel: string;
  entityId: string;
  description: string;
  detectedAt: string;
  status: DataQualityIssueStatus;
  source: string;
  currentData: string;
  externalEvidence: string;
  proposedAction: string;
}

export type AuditLifecycleStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_WARNINGS'
  | 'FAILED'
  | string;

export type AuditEventType = 'SYNC' | 'CORRECTION' | 'DATA_QUALITY' | 'ADMINISTRATION' | string;

export interface AuditEvent {
  eventId: string;
  eventType: AuditEventType;
  executionId: string;
  startedAt: string;
  finishedAt?: string | null;
  durationMs?: number | null;
  actorKind?: 'SYSTEM' | 'USER' | string | null;
  actorId?: string | null;
  actorLabel?: string | null;
  entity: string;
  operation: string;
  source: string;
  syncMode?: string | null;
  status: AuditLifecycleStatus;
  totalExpected?: number | null;
  processed: number;
  created: number;
  updated: number;
  unchanged: number;
  reactivated: number;
  markedInactive: number;
  errorCount: number;
  errorCode?: string | null;
  errorMessage?: string | null;
  sourceSyncExecutionId?: string | null;
  recoveryAvailable?: boolean | null;
  recoveryType?: string | null;
  recoveryBlockedReason?: string | null;
  pendingAuthors?: number | null;
}

export interface AuditChange {
  id: number;
  syncId?: string | null;
  entity?: string | null;
  localRecordId?: number | null;
  pureId?: number | null;
  recordUuid?: string | null;
  recordKey?: string | null;
  changeType?: string | null;
  changeScope?: string | null;
  description?: string | null;
  oldValue?: string | null;
  newValue?: string | null;
  createdAt?: string | null;
}

export interface AuditSyncError {
  id: number;
  syncId?: string | null;
  entity?: string | null;
  localRecordId?: number | null;
  pureId?: number | null;
  uuid?: string | null;
  recordKey?: string | null;
  stage?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  createdAt?: string | null;
}

/** @deprecated Replaced by AuditEvent. Kept only if older mocks still compile. */
export type AuditEntry = AuditEvent;

export type IntegrationHealth = 'operational' | 'warning' | 'pending' | 'unconfigured' | 'error' | 'disabled';

export interface IntegrationEntityRun {
  syncId?: string | null;
  entity?: string | null;
  status?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  totalExpected?: number | null;
  processed?: number | null;
  errors?: number | null;
  syncMode?: string | null;
}

export interface IntegrationEntitySync {
  entity: string;
  latestExecution?: IntegrationEntityRun | null;
}

export interface IntegrationStatus {
  id: string;
  name: string;
  description: string;
  health: IntegrationHealth;
  healthReason?: string | null;
  lastSync?: string;
  lastRun?: string;
  lastSyncMode?: string | null;
  lastFullSyncAt?: string | null;
  lastSuccessfulFullSyncAt?: string | null;
  supportedSyncModes?: string[] | null;
  availableSyncModes?: string[] | null;
  hasValidBaseline?: boolean | null;
  recommendedSyncMode?: string | null;
  processedCount?: number;
  errorCount?: number;
  entities?: IntegrationEntitySync[];
}

export type IndicatorLayer = 1 | 2 | 3;
export type IndicatorGroup = 'resultado' | 'proceso' | 'formacion' | 'anid' | 'madurez';
export type IndicatorValueState = 'measured' | 'pending' | 'unavailable';

export interface InstitutionalIndicator {
  id: string;
  layer: IndicatorLayer;
  group: IndicatorGroup;
  code: string;
  name: string;
  valueState: IndicatorValueState;
  valueLabel: string;
}

export interface OperationalMetric {
  id: string;
  label: string;
  value: string;
  hint: string;
  demo?: boolean;
}

export interface PagedResult<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

export type DirectoryPresenceFilter = 'ACTIVE' | 'INACTIVE' | 'ALL';

export interface DirectoryQuery {
  search?: string;
  presence?: DirectoryPresenceFilter;
  page?: number;
  pageSize?: number;
}

export interface DirectoryPerson {
  id: number;
  uuid: string | null;
  displayName: string | null;
  email: string | null;
  campusName: string | null;
  faculty: string | null;
  positionTitle: string | null;
  activeInDirectory: boolean;
  linkedToPure: boolean;
}

export interface DirectoryEmail {
  email: string | null;
  kind: string | null;
  source: string | null;
  active: boolean;
}

export interface DirectoryPersonDetail extends DirectoryPerson {
  givenName: string | null;
  middleName: string | null;
  familyName: string | null;
  secondFamilyName: string | null;
  facultyRaw: string | null;
  lastSeenAt: string | null;
  removedAt: string | null;
  emails: DirectoryEmail[];
}

export interface DirectoryStats {
  total: number;
  active: number;
  inactive: number;
  withPureLink: number;
}

export interface DirectoryIssue {
  sourceRowNumber: number;
  code: string;
  leftPersonId: number | null;
  rightPersonId: number | null;
  resolved: boolean;
}

export interface DirectoryImportResult {
  dryRun: boolean;
  importPublicId: string | null;
  filename: string | null;
  sheetName: string | null;
  status: string | null;
  rowCount: number;
  created: number;
  updated: number;
  unchanged: number;
  reactivated: number;
  rejected: number;
  wouldInactivate: number;
  inactivated: number;
  issueCount: number;
  censusReliable: boolean;
  unmappedHeaders: string[];
  issues: DirectoryIssue[];
}

export interface DirectoryImportSummary {
  id: number;
  publicId: string | null;
  filename: string | null;
  status: string | null;
  importedBy: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  rowCount: number;
  createdCount: number;
  updatedCount: number;
  unchangedCount: number;
  reactivatedCount: number;
  inactivatedCount: number;
  issueCount: number;
  expectedCount: number;
  processedCount: number;
  errorMessage: string | null;
  heartbeatAt: string | null;
  owned: boolean;
  interrupted: boolean;
}

export interface ResearcherQuery {
  search?: string;
  faculty?: string;
  status?: DataQualityStatus | 'all';
  page?: number;
  pageSize?: number;
}

export type PersonPopulation = 'ALL' | 'RESEARCHERS' | 'WITHOUT_POSITION';
export type SortDirection = 'asc' | 'desc';
export type PersonSort =
  | 'pureId'
  | 'name'
  | 'position'
  | 'orcid'
  | 'publicationCount'
  | 'scopusHindex'
  | 'createdBy'
  | 'modifiedBy';
export type ResearchOutputSort =
  | 'pureId'
  | 'title'
  | 'year'
  | 'type'
  | 'workflow'
  | 'doi'
  | 'doiVersionCount'
  | 'journal'
  | 'contributorsCount'
  | 'fwci'
  | 'createdBy'
  | 'modifiedBy';

export interface PersonIdentity {
  typeDiscriminator?: string | null;
  type?: string | null;
  value?: string | null;
}

export interface Person {
  id: number;
  pureId: number | null;
  uuid: string | null;
  firstName: string | null;
  lastName: string | null;
  fullName: string | null;
  email: string | null;
  position: string | null;
  orcid: string | null;
  orcidAuthenticated: boolean | null;
  scopusIds: string[];
  scopusIdCount: number | null;
  publicationCount: number | null;
  organization: string | null;
  activeInPure?: boolean | null;
  createdBy: string | null;
  modifiedBy: string | null;
  scopusHindex: number | null;
  scopusCitations: number | null;
}

export interface PersonAffiliation {
  personId?: number | null;
  pureId: number | null;
  organizationUuid: string | null;
  organizationId: number | null;
  nameEs: string | null;
  nameEn: string | null;
  typeEs: string | null;
  typeEn: string | null;
  organizationActiveInPure: boolean | null;
  resolved: boolean;
  employmentType: string | null;
  email: string | null;
  startDate: string | null;
  endDate: string | null;
  primaryAssociation: boolean | null;
  contractType: string | null;
  fte: number | null;
  jobDescription: string | null;
  jobTitle: string | null;
  staffType: string | null;
}

export interface PersonDetail extends Person {
  gender: string | null;
  primaryId: string | null;
  identities: PersonIdentity[];
  affiliations: PersonAffiliation[];
  orcidSyncPending?: boolean | null;
  lastOrcidSyncAt?: string | null;
  lastOrcidSyncStatus?: string | null;
  orcidFirstSeenAt?: string | null;
  orcidFirstSeenSource?: 'BASELINE' | 'OBSERVED' | null;
  openalexAuthorId?: string | null;
  openalexDisplayName?: string | null;
  openalexMatchStatus?: string | null;
  openalexUrl?: string | null;
  openalexMatchingWorkCount?: number | null;
  openalexConfirmed?: boolean | null;
}

export type OrcidMatchStatus = 'MATCHED_BY_DOI' | 'ORCID_ONLY' | 'PURE_ONLY' | 'AMBIGUOUS' | 'NO_DOI' | 'INACTIVE';

export interface OrcidWorkExternalId {
  type: string | null;
  value: string | null;
  normalizedValue: string | null;
  url: string | null;
  relationship: string | null;
}

export interface OrcidWork {
  id: number | null;
  workGroupId: number | null;
  putCode: number | null;
  orcid: string | null;
  workType: string | null;
  title: string | null;
  journalTitle: string | null;
  publicationYear: number | null;
  doi: string | null;
  eid: string | null;
  url: string | null;
  sourceName: string | null;
  citationType: string | null;
  detailLoaded: boolean | null;
  contributorCount: number;
  activeInOrcid: boolean | null;
  matchStatus: OrcidMatchStatus | string | null;
  externalIds: OrcidWorkExternalId[];
}

export interface OrcidWorkGroup {
  id: number | null;
  orcid: string | null;
  title: string | null;
  journalTitle: string | null;
  workType: string | null;
  publicationYear: number | null;
  doi: string | null;
  activeInOrcid: boolean | null;
  sourceCount: number;
  sources: string[];
  matchStatus: OrcidMatchStatus | string | null;
  externalIds: OrcidWorkExternalId[];
  assertions: OrcidWork[];
}

export interface PersonOrcidWorks {
  personId: number;
  orcid: string | null;
  orcidValid: boolean | null;
  orcidSyncPending: boolean | null;
  lastOrcidSyncAt: string | null;
  lastOrcidSyncStatus: string | null;
  workGroupCount: number;
  workAssertionCount: number;
  matchedByDoi: number;
  orcidOnly: number;
  noDoi: number;
  pureOnly: number;
  ambiguous: number;
  groups: OrcidWorkGroup[];
  works: OrcidWork[];
}

export interface OrcidStats {
  configured: boolean;
  tablesAvailable: boolean;
  peopleWithOrcid: number;
  peopleSynced: number;
  workGroupCount: number;
  workAssertionCount: number;
  works: number;
  lastProcessed: number | null;
  lastErrors: number | null;
  lastExecution: string | null;
  lastStatus: string | null;
}

export type ResearcherIdentityIndicatorCode =
  | 'scopusCoverage'
  | 'multipleScopusProfiles'
  | 'controlledSignature'
  | 'highSignatureFragmentation'
  | 'normalizedUssOrcidAffiliation';

export type OrcidAffiliationClass = 'NORMALIZED' | 'USS_VARIANT' | 'USS_NOT_FOUND' | 'NOT_EVALUABLE';

export interface IndicatorMeasure {
  numerator: number;
  denominator: number;
  percentage: number | null;
  universe: string | null;
}

export interface ResearcherIdentityIndicators {
  scopusCoverage: IndicatorMeasure;
  multipleScopusProfiles: IndicatorMeasure;
  controlledSignature: IndicatorMeasure;
  highSignatureFragmentation: IndicatorMeasure;
  normalizedUssOrcidAffiliation: IndicatorMeasure;
  orcidNormalizedCount: number;
  orcidVariantCount: number;
  orcidNotFoundCount: number;
  orcidNotEvaluableCount: number;
}

export interface OrcidAdoptionMonth {
  month: string;
  totalPersons: number | null;
  personsWithOrcid: number | null;
  percentage: number | null;
  complete: boolean;
}

export interface OrcidAdoptionSeries {
  metric: string;
  definition: string;
  universe: string;
  availableFrom: string | null;
  from: string;
  to: string;
  months: OrcidAdoptionMonth[];
}

export interface ResearcherIdentityCase {
  personId: number;
  pureId: number | null;
  firstName: string | null;
  lastName: string | null;
  fullName: string;
  position: string | null;
  orcid: string | null;
  scopusDistinctCount: number | null;
  evaluatedPublications: number | null;
  distinctSignatureCount: number | null;
  primarySignature: string | null;
  classification: OrcidAffiliationClass | string | null;
  employmentOrganizationName: string | null;
  employmentOrganizationNames: string[];
}

export interface ResearcherIdentitySignatureVariant {
  displaySignature: string;
  normalizedSignature: string;
  publicationCount: number;
}

export interface ResearcherIdentitySignatureDetail {
  personId: number;
  fullName: string;
  position: string | null;
  evaluatedPublications: number;
  distinctSignatureCount: number;
  variants: ResearcherIdentitySignatureVariant[];
}

export interface ResearcherIdentityCaseQuery {
  indicator: ResearcherIdentityIndicatorCode | string;
  classification?: OrcidAffiliationClass | 'ALL' | string;
  search?: string;
  sort?: string;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}

export type OrcidDiscoveryStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_WARNINGS'
  | 'FAILED'
  | string;

export type OrcidDiscoveryPersonStatus =
  | 'STRONG'
  | 'MANUAL_REVIEW'
  | 'CONFLICT'
  | 'ALREADY_USED'
  | 'NO_CANDIDATE'
  | 'TOO_MANY_CANDIDATES'
  | 'SYNCED'
  | 'REMOTE_CHANGED'
  | 'SYNC_FAILED'
  | 'ERROR'
  | string;

export interface OrcidDiscoveryStats {
  people: number;
  withOrcid: number;
  withoutOrcid: number;
  withCandidates: number;
  strongMatches: number;
  manualReview: number;
  noCandidate: number;
  conflicts: number;
  searchMaxCandidates: number;
}

export interface OrcidDiscoveryExecution {
  id: number;
  status: OrcidDiscoveryStatus;
  startedAt: string | null;
  finishedAt: string | null;
  startedBy: string | null;
  totalPeople: number;
  processedPeople: number;
  peopleWithCandidates: number;
  strongMatchCount: number;
  manualReviewCount: number;
  noCandidateCount: number;
  tooManyCount: number;
  conflictCount: number;
  errorCount: number;
}

export interface OrcidCandidateEvidence {
  id: number;
  researchOutputId: number | null;
  doi: string | null;
  localAuthor: string | null;
  localPosition: number | null;
  orcidAuthor: string | null;
  orcidPosition: number | null;
  nameMatch: boolean | null;
  positionMatch: boolean | null;
  note: string | null;
  personFullName: string | null;
  contributorFullName: string | null;
  personFirstName: string | null;
  personLastName: string | null;
  contributorFirstName: string | null;
  contributorLastName: string | null;
  namePartitionDifferent: boolean | null;
  reasonCodes: string[];
}

export interface OrcidCandidate {
  id: number;
  orcid: string;
  score: number;
  matchLevel: string;
  status: string;
  nameMatch: boolean | null;
  doiMatchCount: number;
  authorMatch: boolean | null;
  positionMatch: boolean | null;
  alreadyUsedPersonId: number | null;
  evidence: OrcidCandidateEvidence[];
}

export interface OrcidDiscoveryPerson {
  id: number;
  personId: number;
  pureId: number | null;
  uuid: string | null;
  firstName: string | null;
  lastName: string | null;
  fullName: string;
  cargo: string | null;
  pureOrcid: string | null;
  foundOrcid: string | null;
  selectedOrcid: string | null;
  bestCandidateId: number | null;
  uniqueStrongCandidateId: number | null;
  personStatus: OrcidDiscoveryPersonStatus;
  statusReason: string | null;
  candidateCount: number;
  searchHitCount: number;
  doiMatchCount: number;
  selectedCandidateId: number | null;
  selectionOrigin: string | null;
  selectedBy: string | null;
  selectedAt: string | null;
  searchGivenName: string | null;
  searchFamilyName: string | null;
  lastError: string | null;
  selectable: boolean;
  selected: boolean;
  selectionDisabledReason: string | null;
  candidates: OrcidCandidate[];
}

export interface OrcidDiscoverySelectedItem {
  personId: number;
  displayName: string | null;
  pureId: number | null;
  orcid: string | null;
  candidateId: number | null;
  personStatus: string | null;
  selectionOrigin: string | null;
  queued: boolean;
}

export interface OrcidDiscoveryApplyItem {
  status: string;
  localParentId: number | null;
  parentUuid: string | null;
  childValue: string | null;
  displayName: string | null;
  pureId: number | null;
  message: string | null;
}

export interface OrcidDiscoveryApplyResult {
  selected: number;
  succeeded: number;
  failed: number;
  items: OrcidDiscoveryApplyItem[];
}

export interface PersonOrcidSyncResult {
  personId: number;
  orcid: string | null;
  status: string;
  received: number;
  created: number;
  updated: number;
  unchanged: number;
  reactivated: number;
  markedInactive: number;
}

export interface PersonScopusIdentifierItem {
  id: number | null;
  pureId: number | null;
  value: string | null;
  foundInScopus: boolean;
  scopusAuthorId: number | null;
  authorName: string | null;
  numberDocuments: number | null;
  orcid: string | null;
}

export interface PersonScopusIdentifiers {
  personId: number;
  count: number;
  distinctCount: number;
  matchedCount: number;
  items: PersonScopusIdentifierItem[];
}

export type PersonOrcidStatus = 'ALL' | 'WITH_ORCID' | 'WITHOUT_ORCID';
export type PersonScopusStatus = 'ALL' | 'WITH_SCOPUS' | 'WITHOUT_SCOPUS';

export interface PersonQuery {
  search?: string;
  cargo?: string;
  population?: PersonPopulation;
  orcidStatus?: PersonOrcidStatus;
  hasOrcid?: boolean;
  scopusStatus?: PersonScopusStatus;
  hasScopus?: boolean;
  scopusCount?: number | null;
  minDistinctScopusCount?: number | null;
  sort?: PersonSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}

export interface PersonFilters {
  positions: string[];
  withoutPositionAvailable: boolean;
  scopusIdCounts: number[];
}

export interface ScopusCountBucket {
  count: number;
  people: number;
}

export interface PersonScopusStats {
  without: number;
  one: number;
  two: number;
  threeOrMore: number;
  maxCount: number;
  distribution: ScopusCountBucket[];
}

export interface PersonStats {
  total: number;
  totalPeople: number;
  researchers: number;
  withoutPosition: number;
  scopus: PersonScopusStats;
  withScopusHindex: number;
}

export type ResearchOutputDoiStatus =
  | 'ALL'
  | 'WITH_DOI'
  | 'WITHOUT_DOI'
  | 'MULTIPLE_DOI_VERSIONS'
  | 'SHARED_DOI';

export interface ResearchOutputQuery {
  search?: string;
  type?: string;
  year?: number;
  hasDoi?: boolean;
  doiStatus?: ResearchOutputDoiStatus;
  workflowStep?: string;
  status?: DataQualityStatus | 'all';
  fwciFrom?: number | null;
  fwciTo?: number | null;
  sort?: ResearchOutputSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}

export interface ResearchOutputStats {
  total: number;
  withDoi: number;
  withoutDoi: number;
  withMultipleDoiVersions: number;
  withSharedDoi: number;
  sharedDoiValues: number;
  doiElectronicVersionsWithoutDoi: number;
  withFwci: number;
}

export interface ResearchOutputItem {
  id: number;
  pureId: number | null;
  uuid: string | null;
  title: string | null;
  year: number | null;
  type: string | null;
  doi: string | null;
  journal: string | null;
  journalUuid: string | null;
  contributorCount: number | null;
  doiVersionCount: number | null;
  activeInPure?: boolean | null;
  workflowStep: string | null;
  workflowEs: string | null;
  workflowEn: string | null;
  visibilityKey: string | null;
  visibilityEs: string | null;
  visibilityEn: string | null;
  createdBy: string | null;
  modifiedBy: string | null;
  fwci: number | null;
  fwciYear: number | null;
}

export interface ResearchOutputTypeOption {
  code: string;
  typeEn: string | null;
  typeEs: string | null;
}

export interface ResearchOutputWorkflowOption {
  step: string;
  labelEn: string | null;
  labelEs: string | null;
}

export interface ResearchOutputFilters {
  types: ResearchOutputTypeOption[];
  years: number[];
  workflows: ResearchOutputWorkflowOption[];
}

export interface ResearchOutputStatusItem {
  current: boolean | null;
  statusEs: string | null;
  statusEn: string | null;
  publicationYear: number | null;
  publicationMonth: number | null;
  publicationDay: number | null;
}

export interface ResearchOutputIdentifierItem {
  typeDiscriminator: string | null;
  idSource: string | null;
  value: string | null;
}

export interface ResearchOutputElectronicVersionItem {
  id: number | null;
  pureId: number | null;
  typeDiscriminator: string | null;
  doi: string | null;
  accessTypeEs: string | null;
  accessTypeEn: string | null;
  versionTypeEs: string | null;
  versionTypeEn: string | null;
}

export interface ResearchOutputDoiVersions {
  researchOutputId: number;
  count: number;
  distinctCount: number;
  items: ResearchOutputElectronicVersionItem[];
}

export interface ResearchOutputLinkItem {
  url: string | null;
  descriptionEs: string | null;
  descriptionEn: string | null;
  linkTypeEs: string | null;
  linkTypeEn: string | null;
}

export interface ResearchOutputContributorItem {
  id: number;
  personId: number | null;
  personUuid: string | null;
  firstName: string | null;
  lastName: string | null;
  fullName: string | null;
  role: string | null;
  roleEs: string | null;
  roleEn: string | null;
  external: boolean | null;
  correspondingAuthor: boolean | null;
  organizationUuids: string[];
}

export interface ResearchOutputKeywordItem {
  type: string | null;
  value: string | null;
  locale: string | null;
}

export interface ResearchOutputKeywordGroupItem {
  name: string | null;
  nameEs: string | null;
  nameEn: string | null;
  keywords: ResearchOutputKeywordItem[];
}

export interface ResearchOutputJournalItem {
  uuid: string | null;
  title: string | null;
  issn: string | null;
  synced: boolean | null;
}

export interface ResearchOutputDetailItem extends ResearchOutputItem {
  typeEs: string | null;
  typeEn: string | null;
  submissionYear: number | null;
  volume: string | null;
  issue: string | null;
  pages: string | null;
  numberOfPages: number | null;
  journalDetail: ResearchOutputJournalItem | null;
  publicationStatuses: ResearchOutputStatusItem[];
  identifiers: ResearchOutputIdentifierItem[];
  electronicVersions: ResearchOutputElectronicVersionItem[];
  contributors: ResearchOutputContributorItem[];
  keywordGroups: ResearchOutputKeywordGroupItem[];
  links: ResearchOutputLinkItem[];
  externalOrganizationUuids: string[];
  doiElectronicVersionCount: number | null;
  distinctDoiCount: number | null;
}

export interface DataQualityQuery {
  search?: string;
  status?: DataQualityIssueStatus | 'all';
  page?: number;
  pageSize?: number;
}

export type DataQualityFindingStatus = 'OPEN' | 'IN_REVIEW' | 'RESOLVED' | 'NOT_APPLICABLE';
export type DataQualityActionType = 'SAFE_SUGGESTION' | 'MANUAL_REVIEW' | 'VIEW_ONLY';
export type DataQualityTriggerType = 'AFTER_SYNC' | 'MANUAL' | 'AFTER_CORRECTION';
export type DataQualityCategory =
  | 'IDENTIFICATION'
  | 'COMPLETENESS'
  | 'CONSISTENCY'
  | 'TEXT_QUALITY'
  | 'IDENTITY'
  | 'BIBLIOGRAPHY'
  | 'ACCESS_WORKFLOW'
  | 'EXTERNAL_CONSISTENCY';

export interface DataQualityFindingQuery {
  search?: string;
  category?: string;
  rule?: string;
  entity?: string;
  action?: string;
  status?: string;
  source?: string;
  sort?: string;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}

export interface DataQualityFinding {
  id: number;
  ruleId: number;
  ruleCode: string;
  ruleName: string;
  category: string;
  entityType: string;
  localRecordId: number;
  pureId: number | null;
  recordUuid: string | null;
  recordLabel: string | null;
  fieldName: string | null;
  issueKind: string;
  correctionMode: DataQualityActionType | string;
  status: DataQualityFindingStatus | string;
  summary: string;
  currentValue: string | null;
  suggestedValue: string | null;
  evidenceJson: string | null;
  evidences: DataQualityFindingEvidence[];
  description: string | null;
  ruleText: string | null;
  alertText: string | null;
  firstDetectedAt: string | null;
  lastDetectedAt: string | null;
  resolvedAt: string | null;
  resolvedBy: string | null;
  resolutionNote: string | null;
  recordRoute: string | null;
}

export interface DataQualityFindingEvidence {
  id?: number | null;
  source: string | null;
  sourceRecordId: string | null;
  sourceField: string | null;
  externalValue: string | null;
  normalizedExternalValue: string | null;
  comparisonResult: string | null;
  externalLastSyncAt: string | null;
  metadataJson: string | null;
}

export interface DataQualityRule {
  id: number;
  code: string;
  sourceFamily: string;
  entityType: string;
  fieldName: string | null;
  name: string;
  description: string;
  ruleText: string;
  alertText: string | null;
  bulkProcedure: string | null;
  category: string;
  correctionMode: string;
  enabled: boolean;
  openFindingCount: number;
}

export interface DataQualityExecution {
  id: number;
  status: string;
  triggerType: string;
  scope: string;
  entityType: string | null;
  localRecordId: number | null;
  syncExecutionId: number | null;
  startedAt: string | null;
  finishedAt: string | null;
  totalRecords: number;
  evaluatedRecords: number;
  findingCount: number;
  resolvedCount: number;
  errorCount: number;
  startedBy: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  durationSeconds: number | null;
}

export interface DataQualityStats {
  openFindings: number;
  affectedRecords: number;
  correctionAvailable: number;
  manualReview: number;
  viewOnly: number;
  rulesWithFindings: number;
  ruleCounts: DataQualityRuleCount[];
}

export interface DataQualityRuleCount {
  code: string;
  name: string;
  sourceFamily?: string;
  count: number;
}

export interface DataQualityApplyItem {
  findingId: number;
  ruleCode: string;
  recordLabel: string | null;
  status: string;
  message: string | null;
  currentValue: string | null;
  suggestedValue: string | null;
}

export interface DataQualityApplyResult {
  applied: number;
  remoteChanged: number;
  localFailed: number;
  reviewRequired: number;
  failed: number;
  items: DataQualityApplyItem[];
}

export interface AuditQuery {
  search?: string;
  from?: string;
  to?: string;
  eventType?: string;
  source?: string;
  entity?: string;
  status?: string;
  syncMode?: string;
  actor?: string;
  page?: number;
  pageSize?: number;
  sort?: string;
  direction?: string;
}

export interface UiState<T> {
  status: 'loading' | 'success' | 'empty' | 'error';
  data?: T;
  message?: string;
}

export interface AdminPlaceholderSection {
  id: string;
  title: string;
  description: string;
}

export interface PureCorrectionResult {
  status: string;
  entity: string | null;
  scope: string | null;
  localParentId: number | null;
  parentUuid: string | null;
  childPureId: number | null;
  childValue: string | null;
  message: string | null;
}

export type JournalSort =
  | 'pureId'
  | 'title'
  | 'publicationCount'
  | 'issn'
  | 'scopusId'
  | 'type'
  | 'workflow'
  | 'citeScore'
  | 'quartile'
  | 'createdBy'
  | 'modifiedBy';
export type JournalIssnCountFilter = '' | '0' | '1' | '2+';
export type JournalPublicationFilter = '' | 'WITH_PUBLICATIONS' | 'WITHOUT_PUBLICATIONS';
export type JournalCiteScoreFilter = '' | 'WITH' | 'WITHOUT';
export type JournalQuartileFilter = '' | 'Q1' | 'Q2' | 'Q3' | 'Q4';

export interface JournalQuery {
  search?: string;
  typeUri?: string;
  workflowStep?: string;
  issnCount?: JournalIssnCountFilter;
  publications?: JournalPublicationFilter;
  citescore?: JournalCiteScoreFilter;
  quartile?: JournalQuartileFilter;
  sort?: JournalSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}

export interface JournalStats {
  total: number;
  withoutIssn: number;
  oneIssn: number;
  twoOrMoreIssn: number;
  withScopusId: number;
  withoutPublications: number;
  unresolvedResearchOutputRefs: number;
  withCiteScore: number;
  withQuartile: number;
}

export interface JournalTypeOption {
  uri: string;
  typeEn: string | null;
  typeEs: string | null;
}

export interface JournalWorkflowOption {
  step: string;
  labelEn: string | null;
  labelEs: string | null;
}

export interface JournalFilters {
  types: JournalTypeOption[];
  workflows: JournalWorkflowOption[];
}

export interface JournalItem {
  id: number;
  pureId: number | null;
  uuid: string | null;
  title: string | null;
  issns: string[];
  issnCount: number | null;
  publicationCount: number;
  scopusId: string | null;
  type: string | null;
  typeEs: string | null;
  typeEn: string | null;
  workflowStep: string | null;
  workflowEs: string | null;
  workflowEn: string | null;
  activeInPure?: boolean | null;
  createdBy: string | null;
  modifiedBy: string | null;
  citeScore: number | null;
  citeScoreYear: number | null;
  quartilesCompact: string | null;
  quartileYear: number | null;
  quartileCount: number | null;
}

export interface JournalTitleItem {
  pureId: number | null;
  title: string | null;
}

export interface JournalIdentifierItem {
  pureId: number | null;
  typeDiscriminator: string | null;
  idSource: string | null;
  value: string | null;
  typeUri: string | null;
  typeEn: string | null;
  typeEs: string | null;
}

export interface JournalClassificationItem {
  uri: string | null;
  termEn: string | null;
  termEs: string | null;
}

export interface JournalKeywordGroupItem {
  pureId: number | null;
  typeDiscriminator: string | null;
  logicalName: string | null;
  nameEn: string | null;
  nameEs: string | null;
  classifications: JournalClassificationItem[];
}

export interface JournalDetail extends JournalItem {
  publisherUuid: string | null;
  publisherSystemName: string | null;
  createdDatePure: string | null;
  modifiedDatePure: string | null;
  version: string | null;
  systemName: string | null;
  titles: JournalTitleItem[];
  identifiers: JournalIdentifierItem[];
  keywordGroups: JournalKeywordGroupItem[];
}

export interface JournalIssnItem {
  id: number | null;
  pureId: number | null;
  issn: string | null;
}

export interface JournalIssns {
  journalId: number;
  journalPureId?: number | null;
  journalUuid?: string | null;
  count: number;
  items: JournalIssnItem[];
}

export interface MetricObservation {
  collectionId: string | null;
  sourceUri: string | null;
  sourceTermEs: string | null;
  sourceTermEn: string | null;
  categoryUri: string | null;
  categoryTermEs: string | null;
  categoryTermEn: string | null;
  metricYear: number | null;
  metricId: string | null;
  valueKind: string | null;
  integerValue: number | null;
  decimalValue: number | null;
  stringValue: string | null;
  lastSyncedAt: string | null;
}

export interface MetricFetchState {
  collectionId: string | null;
  lastOutcome: string | null;
  lastHttpStatus: number | null;
  lastErrorCode: string | null;
  lastErrorMessage: string | null;
  observationCount: number | null;
  lastSyncedAt: string | null;
  lastSuccessAt: string | null;
}

export interface JournalMetrics {
  journalId: number;
  uuid: string | null;
  scopusJournal: MetricFetchState | null;
  scopusCitescore: MetricFetchState | null;
  latestCiteScore: { value: number | null; year: number | null; lastSyncedAt: string | null } | null;
  latestQuartiles: {
    year: number | null;
    compactLabel: string | null;
    multipleCategories: boolean;
    categories: Array<{
      year: number | null;
      categoryUri: string | null;
      categoryTermEs: string | null;
      categoryTermEn: string | null;
      quartile: number | null;
      percentile: number | null;
      rank: number | null;
    }>;
    lastSyncedAt: string | null;
  } | null;
  citeScoreHistory: MetricObservation[];
  quartileHistory: Array<{
    year: number | null;
    categoryUri: string | null;
    categoryTermEs: string | null;
    categoryTermEn: string | null;
    quartile: number | null;
    percentile: number | null;
    rank: number | null;
  }>;
  quartileRows: MetricObservation[];
  otherScopusJournal: MetricObservation[];
  quartileTotal: number;
}

export interface ResearchOutputMetrics {
  researchOutputId: number;
  uuid: string | null;
  sciVal: MetricFetchState | null;
  fwci: { value: number | null; year: number | null; sourceTermEn: string | null; lastSyncedAt: string | null } | null;
  observations: MetricObservation[];
}

export interface PersonMetrics {
  personId: number;
  uuid: string | null;
  hIndex: MetricFetchState | null;
  scopusAllYears: {
    hindex: number | null;
    totalCitations: number | null;
    sourceTermEn: string | null;
    sourceTermEs: string | null;
    categoryTermEn: string | null;
    categoryTermEs: string | null;
    lastSyncedAt: string | null;
  } | null;
  observations: MetricObservation[];
}

export type ProjectSort =
  | 'pureId'
  | 'title'
  | 'acronym'
  | 'projectId'
  | 'type'
  | 'periodStart'
  | 'periodEnd'
  | 'principalInvestigator'
  | 'workflow';
export type ProjectPresenceFilter = 'ACTIVE' | 'INACTIVE' | 'ALL';

export interface ProjectQuery {
  search?: string;
  typeUri?: string;
  workflowStep?: string;
  visibilityKey?: string;
  presence?: ProjectPresenceFilter;
  startYear?: number | null;
  sort?: ProjectSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}

export interface ProjectStats {
  total: number;
  withProjectId: number;
  withFunderReference: number;
  withPrincipalInvestigator: number;
}

export interface ProjectTypeOption {
  uri: string;
  typeEn: string | null;
  typeEs: string | null;
}

export interface ProjectWorkflowOption {
  step: string;
  labelEn: string | null;
  labelEs: string | null;
}

export interface ProjectVisibilityOption {
  key: string;
  labelEn: string | null;
  labelEs: string | null;
}

export interface ProjectFilters {
  types: ProjectTypeOption[];
  workflows: ProjectWorkflowOption[];
  visibilities: ProjectVisibilityOption[];
  startYears: number[];
}

export interface ProjectOrganization {
  organizationUuid: string | null;
  organizationSystemName: string | null;
  managing?: boolean;
  organizationId?: number | null;
  nameEs?: string | null;
  nameEn?: string | null;
  typeEs?: string | null;
  typeEn?: string | null;
  activeInPure?: boolean | null;
  resolved?: boolean;
}

export interface ProjectParticipant {
  id?: number | null;
  pureId: number | null;
  typeDiscriminator: string | null;
  firstName: string | null;
  lastName: string | null;
  displayName: string | null;
  roleUri: string | null;
  roleTermEn: string | null;
  roleTermEs: string | null;
  associationHidden: boolean | null;
  personUuid: string | null;
  personId: number | null;
  organizations: ProjectOrganization[];
}

export interface ProjectIdentifier {
  pureId: number | null;
  typeDiscriminator: string | null;
  value: string | null;
  typeUri: string | null;
  typeEn: string | null;
  typeEs: string | null;
}

export interface ProjectDescription {
  pureId: number | null;
  typeUri: string | null;
  typeEn: string | null;
  typeEs: string | null;
}

export interface ProjectNatureType {
  uri: string | null;
  termEn: string | null;
  termEs: string | null;
}

export interface ProjectItem {
  id: number;
  pureId: number | null;
  uuid: string | null;
  title: string | null;
  titleEs: string | null;
  titleEn: string | null;
  acronym: string | null;
  projectId: string | null;
  funderReference: string | null;
  type: string | null;
  typeEs: string | null;
  typeEn: string | null;
  periodStartDate: string | null;
  periodEndDate: string | null;
  principalInvestigatorName: string | null;
  principalInvestigatorPersonId: number | null;
  principalInvestigatorPersonUuid: string | null;
  managingOrganizationUuid: string | null;
  managingOrganizationId?: number | null;
  managingOrganizationName?: string | null;
  managingOrganizationNameEs?: string | null;
  managingOrganizationNameEn?: string | null;
  managingOrganizationResolved?: boolean;
  workflowStep: string | null;
  workflowEs: string | null;
  workflowEn: string | null;
  visibilityKey: string | null;
  visibilityEs: string | null;
  visibilityEn: string | null;
  activeInPure?: boolean | null;
  portalUrl: string | null;
}

export interface ProjectDetail extends ProjectItem {
  typeDiscriminator: string | null;
  createdBy: string | null;
  createdDatePure: string | null;
  modifiedBy: string | null;
  modifiedDatePure: string | null;
  version: string | null;
  typeUri: string | null;
  workflowDescriptionEn: string | null;
  workflowDescriptionEs: string | null;
  systemName: string | null;
  prettyUrlIdentifiers: string[];
  participants: ProjectParticipant[];
  identifiers: ProjectIdentifier[];
  descriptions: ProjectDescription[];
  natureTypes: ProjectNatureType[];
  organizations: ProjectOrganization[];
}

export type OrganizationSort = 'pureId' | 'name' | 'type' | 'parent' | 'modifiedDatePure' | 'activeInPure';
export type OrganizationPresenceFilter = 'ACTIVE' | 'INACTIVE' | 'ALL';
export type OrganizationParentFilter = 'WITH' | 'WITHOUT' | '';

export interface OrganizationQuery {
  search?: string;
  typeUri?: string;
  presence?: OrganizationPresenceFilter;
  hasParent?: OrganizationParentFilter;
  sort?: OrganizationSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}

export interface OrganizationTypeOption {
  uri: string;
  typeEn: string | null;
  typeEs: string | null;
}

export interface OrganizationFilters {
  types: OrganizationTypeOption[];
}

export interface OrganizationStats {
  total: number;
  active: number;
  inactive: number;
  withParent: number;
}

export interface OrganizationItem {
  id: number;
  pureId: number | null;
  uuid: string | null;
  name: string | null;
  nameEs: string | null;
  nameEn: string | null;
  type: string | null;
  typeEs: string | null;
  typeEn: string | null;
  typeUri: string | null;
  parentUuid: string | null;
  parentName: string | null;
  parentNameEs: string | null;
  parentNameEn: string | null;
  activeInPure?: boolean | null;
  modifiedDatePure: string | null;
  createdBy: string | null;
  modifiedBy: string | null;
}

export interface OrganizationDetail extends OrganizationItem {
  typeDiscriminator: string | null;
  createdDatePure: string | null;
  version: string | null;
  systemName: string | null;
  parentId: number | null;
  parentResolved: boolean | null;
  parentActiveInPure: boolean | null;
}

export interface OrganizationPerson {
  personId: number | null;
  personPureId: number | null;
  personUuid: string | null;
  firstName: string | null;
  lastName: string | null;
  displayName: string | null;
  jobTitle: string | null;
  employmentType: string | null;
  fte: number | null;
  startDate: string | null;
  endDate: string | null;
  primaryAssociation: boolean | null;
  personActiveInPure: boolean | null;
}

export interface OrganizationProject {
  projectId: number | null;
  projectPureId: number | null;
  projectUuid: string | null;
  title: string | null;
  titleEs: string | null;
  titleEn: string | null;
  relationType: string | null;
  projectActiveInPure: boolean | null;
}

export interface UnpaywallStats {
  configured: boolean;
  tablesAvailable: boolean;
  uniqueDois: number;
  evaluated: number;
  oa: number;
  nonOa: number;
  repositoryCopy: number;
  notFound: number;
  lastProcessed: number | null;
  lastErrors: number | null;
  lastNotFound: number | null;
  lastOa: number | null;
  lastNonOa: number | null;
  lastExecution: string | null;
  lastStatus: string | null;
  oaStatusDistribution: UnpaywallStatusCount[];
}

export interface UnpaywallStatusCount {
  oaStatus: string;
  count: number;
}

export interface UnpaywallLinkedOutput {
  researchOutputId: number;
  electronicVersionId: number | null;
  pureId: number | null;
  uuid: string | null;
  title: string | null;
}

export interface UnpaywallWorkSummary {
  id: number;
  doi: string | null;
  normalizedDoi: string | null;
  doiUrl: string | null;
  lastOutcome: string | null;
  isOa: boolean | null;
  oaStatus: string | null;
  title: string | null;
  year: number | null;
  publisher: string | null;
  journalName: string | null;
  journalIsInDoaj: boolean | null;
  hasRepositoryCopy: boolean | null;
  bestOaHostType: string | null;
  bestOaLicense: string | null;
  bestOaVersion: string | null;
  bestOaUrl: string | null;
  bestOaLandingPageUrl: string | null;
  bestOaPdfUrl: string | null;
  retrievedAt: string | null;
  apiUpdatedAt: string | null;
  researchOutputCount: number;
  publicationTitle: string | null;
  researchOutputs: UnpaywallLinkedOutput[];
}

export interface UnpaywallLocation {
  id: number;
  hostType: string | null;
  license: string | null;
  version: string | null;
  url: string | null;
  urlForLandingPage: string | null;
  urlForPdf: string | null;
  oaDate: string | null;
  best: boolean;
  evidence: string | null;
  repositoryInstitution: string | null;
  endpointId: string | null;
}

export interface UnpaywallWorkDetail extends UnpaywallWorkSummary {
  genre: string | null;
  publishedDate: string | null;
  journalIssns: string | null;
  journalIssnL: string | null;
  journalIsOa: boolean | null;
  lastHttpStatus: number | null;
  lastErrorCode: string | null;
  lastErrorMessage: string | null;
  locations: UnpaywallLocation[];
}

export interface UnpaywallFilters {
  oaStatuses: string[];
  hostTypes: string[];
  licenses: string[];
  versions: string[];
}

export type UnpaywallSort =
  | 'doi'
  | 'title'
  | 'isOa'
  | 'oaStatus'
  | 'bestOaHostType'
  | 'bestOaLicense'
  | 'bestOaVersion'
  | 'retrievedAt'
  | 'year'
  | 'journalName'
  | 'hasRepositoryCopy';

export interface UnpaywallQuery {
  search?: string;
  oaStatus?: string;
  hostType?: string;
  license?: string;
  version?: string;
  isOa?: boolean;
  repositoryCopy?: boolean;
  outcome?: string;
  sort?: UnpaywallSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}

export interface CrossrefStats {
  configured: boolean;
  enabled: boolean;
  tablesAvailable: boolean;
  uniqueDois: number;
  evaluated: number;
  found: number;
  otherAgency: number;
  notFound: number;
  invalid: number;
  withDifferences: number;
  lastProcessed: number | null;
  lastErrors: number | null;
  lastExecution: string | null;
  lastStatus: string | null;
  withOrcid: number;
  withFunding: number;
  withRor: number;
  withUpdate: number;
}

export interface CrossrefLinkedOutput {
  researchOutputId: number;
  electronicVersionId: number | null;
  pureId: number | null;
  uuid: string | null;
  title: string | null;
  activeInPure: boolean;
}

export interface CrossrefWorkSummary {
  id: number;
  doi: string | null;
  normalizedDoi: string | null;
  canonicalDoi: string | null;
  status: string | null;
  registrationAgency: string | null;
  workType: string | null;
  title: string | null;
  containerTitle: string | null;
  publisher: string | null;
  publishedDate: string | null;
  comparisonStatus: string | null;
  issnList: string | null;
  hasOrcid: boolean;
  hasAffiliation: boolean;
  hasRor: boolean;
  hasFunding: boolean;
  hasLicense: boolean;
  hasUpdate: boolean;
  hasRetraction: boolean;
  lastCrossrefSyncAt: string | null;
  researchOutputCount: number;
  publicationTitle: string | null;
  researchOutputs: CrossrefLinkedOutput[];
}

export interface CrossrefAffiliation {
  name: string | null;
  ror: string | null;
  sequenceNo: number;
}

export interface CrossrefAuthor {
  sequenceNo: number;
  givenName: string | null;
  familyName: string | null;
  fullName: string | null;
  orcid: string | null;
  authenticatedOrcid: boolean | null;
  affiliations: CrossrefAffiliation[];
}

export interface CrossrefIssn {
  issn: string | null;
  type: string | null;
}

export interface CrossrefFunder {
  name: string | null;
  funderDoi: string | null;
  award: string | null;
}

export interface CrossrefLicense {
  url: string | null;
  startDate: string | null;
  contentVersion: string | null;
  delayInDays: number | null;
}

export interface CrossrefRelation {
  type: string | null;
  doi: string | null;
  id: string | null;
  assertedBy: string | null;
}

export interface CrossrefWorkDetail extends CrossrefWorkSummary {
  subtitle: string | null;
  volume: string | null;
  issue: string | null;
  pageRange: string | null;
  articleNumber: string | null;
  language: string | null;
  referenceCount: number | null;
  isReferencedByCount: number | null;
  publishedPrintDate: string | null;
  publishedOnlineDate: string | null;
  issuedDate: string | null;
  lastHttpStatus: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  rawJson: string | null;
  authors: CrossrefAuthor[];
  issns: CrossrefIssn[];
  funders: CrossrefFunder[];
  licenses: CrossrefLicense[];
  relations: CrossrefRelation[];
}

export interface CrossrefFilters {
  statuses: string[];
  workTypes: string[];
}

export type CrossrefSort =
  | 'doi'
  | 'pureTitle'
  | 'title'
  | 'type'
  | 'container'
  | 'publishedDate'
  | 'status'
  | 'syncedAt';

export interface CrossrefQuery {
  search?: string;
  status?: string;
  workType?: string;
  hasOrcid?: boolean;
  hasAffiliation?: boolean;
  hasRor?: boolean;
  hasFunding?: boolean;
  hasLicense?: boolean;
  hasUpdate?: boolean;
  comparison?: string;
  sort?: CrossrefSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}

export interface DspaceStats {
  configured: boolean;
  enabled: boolean;
  tablesAvailable: boolean;
  dspaceVersion: string | null;
  items: number;
  communities: number;
  collections: number;
  openAccess: number;
  metadataOnly: number;
  restricted: number;
  embargo: number;
  fulltextPublic: number;
  matched: number;
  candidates: number;
  linkedItems: number;
  reviewItems: number;
  unmatchedItems: number;
  missingItems: number;
  errors: number;
  sharedDois: number;
  sharedDoiItems: number;
  withFile: number;
  withoutFile: number;
  unknownAccess: number;
  bitstreamCount: number;
  lastProcessed: number | null;
  lastErrors: number | null;
  lastExecution: string | null;
  lastStatus: string | null;
}

export type DspaceSort =
  | 'title'
  | 'doi'
  | 'handle'
  | 'collection'
  | 'community'
  | 'pureTitle'
  | 'method'
  | 'access'
  | 'pureAccess'
  | 'comparison'
  | 'file'
  | 'pure'
  | 'status'
  | 'syncedAt'
  | 'name'
  | 'items';
export type DspaceTab = 'items' | 'matches' | 'collections' | 'communities' | 'incidents' | 'history';
export type DspaceMatchKind = 'LINKED' | 'REVIEW' | 'UNMATCHED';

export interface DspaceItemQuery {
  search?: string;
  outcome?: string;
  accessStatus?: string;
  matchStatus?: string;
  matchMethod?: string;
  collectionUuid?: string;
  communityUuid?: string;
  doi?: string;
  sharedDoi?: boolean;
  hasFile?: string;
  accessComparison?: string;
  uuids?: string[];
  sort?: DspaceSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}

export interface DspaceItemList {
  uuid: string;
  handle: string | null;
  handleUrl: string | null;
  name: string | null;
  dcType: string | null;
  doi: string | null;
  normalizedDoi: string | null;
  accessStatus: string | null;
  fulltextAvailability: string | null;
  collectionUuid: string | null;
  collectionName: string | null;
  communityUuid: string | null;
  communityName: string | null;
  communityPath: string | null;
  lastOutcome: string | null;
  lastSyncAt: string | null;
  lastModifiedRemote: string | null;
  matchMethod: string | null;
  matchStatus: string | null;
  canonical: boolean;
  confidence: number | null;
  researchOutputId: number | null;
  researchOutputUuid: string | null;
  researchOutputTitle: string | null;
  sharedDoiCount: number | null;
  hasOriginalFile: boolean;
  dspaceAccessCanonical: string | null;
  pureAccessCanonical: string | null;
  pureAccessRaw: string | null;
  accessComparison: string | null;
}

export interface DspaceBitstream {
  uuid: string;
  name: string | null;
  mimeType: string | null;
  sizeBytes: number | null;
  bundleName: string | null;
  accessStatus: string | null;
  publicDownload: boolean;
  contentUrl: string | null;
  checksumMd5: string | null;
  sequenceId: number | null;
}

export interface DspaceMetadata {
  field: string | null;
  place: number;
  language: string | null;
  value: string | null;
}

export interface DspaceMatchRow {
  id: number | null;
  itemUuid: string | null;
  itemName: string | null;
  itemHandle: string | null;
  itemDoi: string | null;
  matchMethod: string | null;
  matchStatus: string | null;
  canonical: boolean;
  confidence: number | null;
  evidenceJson: string | null;
  updatedAt: string | null;
  researchOutputId: number | null;
  researchOutputUuid: string | null;
  researchOutputTitle: string | null;
  researchOutputDoi: string | null;
}

export interface DspaceItemDetail extends DspaceItemList {
  embargoDate: string | null;
  inArchive: boolean | null;
  discoverable: boolean | null;
  withdrawn: boolean | null;
  lastErrorCode: string | null;
  lastErrorMessage: string | null;
  lastSyncExecutionId: string | null;
  seenInExecutionId: string | null;
  bitstreams: DspaceBitstream[];
  metadata: DspaceMetadata[];
  matches: DspaceMatchRow[];
  collections?: DspaceCollectionRef[];
}

export interface DspaceCollectionRef {
  uuid: string;
  name: string | null;
  relation: string | null;
}

export interface DspaceCollectionList {
  uuid: string;
  handle: string | null;
  name: string | null;
  communityUuid: string | null;
  communityName: string | null;
  archivedItemsCount: number | null;
  itemCount: number;
  lastSyncAt: string | null;
}

export interface DspaceCommunityList {
  uuid: string;
  handle: string | null;
  name: string | null;
  parentUuid: string | null;
  parentName: string | null;
  archivedItemsCount: number | null;
  collectionCount: number;
  itemCount: number;
  lastSyncAt: string | null;
}

export interface DspaceIncident {
  id?: number;
  syncId?: string;
  entity?: string;
  localRecordId?: number | null;
  pureId?: number | null;
  uuid?: string | null;
  recordKey?: string | null;
  stage?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  createdAt?: string | null;
}

export interface DspaceItemSummary {
  uuid: string;
  handle: string | null;
  handleUrl: string | null;
  name: string | null;
  dcType: string | null;
  doi: string | null;
  normalizedDoi: string | null;
  accessStatus: string | null;
  fulltextAvailability: string | null;
  embargoDate: string | null;
  matchMethod: string | null;
  matchStatus: string | null;
  canonical: boolean;
  contentUrl: string | null;
  publicDownload: boolean;
}

export interface OpenAlexStats {
  configured: boolean;
  enabled: boolean;
  tablesAvailable: boolean;
  uniqueDois: number;
  evaluated: number;
  found: number;
  notFound: number;
  invalid: number;
  authors: number;
  personsLinked: number;
  pendingMatches: number;
  confirmedMatches: number;
  conflicts: number;
  lastProcessed: number | null;
  lastErrors: number | null;
  lastExecution: string | null;
  lastStatus: string | null;
  coverageNumerator: number;
  coverageDenominator: number;
  pendingRecoveries?: Array<{
    syncId: string;
    status: string;
    syncMode?: string | null;
    pendingAuthors?: number | null;
    recoveryAvailable?: boolean | null;
    recoveryBlockedReason?: string | null;
    processed: number;
    totalExpected?: number | null;
    errorCode?: string | null;
    phase?: string | null;
  }>;
}

export interface OpenAlexFilters {
  statuses: string[];
  matchStatuses: string[];
  workTypes: string[];
  positions: string[];
}

export type OpenAlexWorkSort = 'doi' | 'title' | 'year' | 'type' | 'cited' | 'status' | 'syncedAt';
export type OpenAlexAuthorSort = 'name' | 'orcid' | 'works' | 'cited' | 'syncedAt';
export type OpenAlexMatchSort = 'person' | 'openalex' | 'works' | 'status' | 'evaluated';

export interface OpenAlexWorkQuery {
  search?: string;
  status?: string;
  workType?: string;
  sort?: OpenAlexWorkSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}

export interface OpenAlexAuthorQuery {
  search?: string;
  sort?: OpenAlexAuthorSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}

export interface OpenAlexMatchQuery {
  search?: string;
  status?: string;
  position?: string;
  hasOrcid?: boolean;
  ussAffiliation?: boolean;
  minWorks?: number;
  sort?: OpenAlexMatchSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}

export interface OpenAlexWorkSummary {
  id: number;
  openalexWorkId: string | null;
  doi: string | null;
  normalizedDoi: string | null;
  displayName: string | null;
  publicationYear: number | null;
  workType: string | null;
  citedByCount: number | null;
  authorshipsCount: number | null;
  authorshipsTruncated: boolean;
  rawJsonTruncated?: boolean | null;
  lastOutcome: string | null;
  lastSyncAt: string | null;
  publicationTitle: string | null;
  researchOutputCount: number;
}

export interface OpenAlexAuthorSummary {
  id: number;
  openalexAuthorId: string | null;
  displayName: string | null;
  orcid: string | null;
  worksCount: number | null;
  citedByCount: number | null;
  lastOutcome: string | null;
  lastSyncAt: string | null;
  openalexUrl: string | null;
  linkedPersonCount: number;
}

export interface OpenAlexMatchSummary {
  id: number;
  personId: number;
  personPureId: number | null;
  personUuid: string | null;
  personName: string | null;
  personPosition: string | null;
  personOrcid: string | null;
  openalexAuthorId: string | null;
  openalexDisplayName: string | null;
  openalexOrcid: string | null;
  openalexUrl: string | null;
  status: string | null;
  matchMethod: string | null;
  selected: boolean;
  selectedManually: boolean;
  matchingWorkCount: number;
  orcidMatch: boolean;
  ussAffiliation: boolean;
  reviewRequested: boolean;
  lastEvaluatedAt: string | null;
  confirmedAt: string | null;
  confirmedBy: string | null;
  identityConfirmed?: boolean;
  orcidComparison?: string | null;
  orcidSyncAvailable?: boolean;
  orcidSyncBlockedReason?: string | null;
}

export interface OpenAlexMatchWorkEvidence {
  id?: number | null;
  authorshipId?: number | null;
  sourceOrder?: number | null;
  rawAuthorName: string | null;
  authorDisplayName?: string | null;
  openalexAuthorId?: string | null;
  authorPosition?: string | null;
  affiliationDisplay?: string | null;
  ror?: string | null;
  nameCompatible?: boolean | null;
  namePartitionDifferent?: boolean | null;
  positionCode?: string | null;
  orcidPure?: string | null;
  orcidOpenAlex?: string | null;
  ussAffiliation?: boolean;
  reasonCodes?: string | null;
  reasonCodeList?: string[] | null;
}

export interface OpenAlexMatchWork {
  id?: number | null;
  researchOutputId: number | null;
  normalizedDoi: string | null;
  openalexWorkId: string | null;
  title: string | null;
  contributorFirstName: string | null;
  contributorLastName: string | null;
  contributorPosition: number | null;
  contributorCount: number | null;
  rawAuthorName: string | null;
  authorPosition: string | null;
  sourceOrder: number | null;
  nameCompatible: boolean | null;
  namePartitionDifferent: boolean | null;
  positionCode: string | null;
  orcidPure: string | null;
  orcidOpenAlex: string | null;
  ussAffiliation: boolean;
  affiliationDisplay: string | null;
  ror: string | null;
  reasonCodes: string | null;
  reasonCodeList?: string[] | null;
  evidences?: OpenAlexMatchWorkEvidence[];
}

export interface OpenAlexMatchDetail extends OpenAlexMatchSummary {
  conclusion: string | null;
  works: OpenAlexMatchWork[];
  worksTotal?: number | null;
  workPage?: number | null;
  workSize?: number | null;
}

