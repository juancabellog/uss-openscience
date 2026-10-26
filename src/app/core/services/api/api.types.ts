export interface ApiMetric {
  code: string;
  value: number | null;
  unit: string | null;
  available: boolean;
  numerator?: number | null;
  denominator?: number | null;
  source?: string | null;
  calculatedAt?: string | null;
  unavailableReason?: string | null;
}

export interface ApiDataQualitySummary {
  available: boolean;
  open?: number | null;
  inReview?: number | null;
  validated?: number | null;
  corrected?: number | null;
  dismissed?: number | null;
}

export interface ApiDashboardMetrics {
  registeredPersons: ApiMetric;
  researchers: ApiMetric;
  personsWithOrcid: ApiMetric;
  orcidAuthenticated?: ApiMetric;
  orcidCoveragePercent?: ApiMetric;
  researchersWithOrcid?: ApiMetric;
  researchersWithOrcidPercent?: ApiMetric;
  personsWithScopusId: ApiMetric;
  pendingDataQualityIssues: ApiMetric;
}

export interface ApiDashboard {
  lastUpdated: string;
  metrics: ApiDashboardMetrics;
  dataQuality: ApiDataQualitySummary;
  integrations: ApiIntegrationStatus[];
}

export type DashboardResponse = ApiDashboard;

export interface ApiPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiIdentifier {
  typeDiscriminator?: string | null;
  type?: string | null;
  value?: string | null;
}

export interface ApiResearchOutputSummary {
  id: number;
  pureId?: number | null;
  uuid?: string | null;
  title?: string | null;
  year?: number | null;
  type?: string | null;
  doi?: string | null;
  journal?: string | null;
  journalUuid?: string | null;
  contributorCount?: number | null;
  doiVersionCount?: number | null;
  activeInPure?: boolean | null;
  workflowStep?: string | null;
  workflowEs?: string | null;
  workflowEn?: string | null;
  visibilityKey?: string | null;
  visibilityEs?: string | null;
  visibilityEn?: string | null;
  createdBy?: string | null;
  modifiedBy?: string | null;
  fwci?: number | null;
  fwciYear?: number | null;
}

export interface ApiResearchOutputType {
  code: string;
  typeEn?: string | null;
  typeEs?: string | null;
}

export interface ApiResearchOutputWorkflowOption {
  step?: string | null;
  labelEn?: string | null;
  labelEs?: string | null;
}

export interface ApiResearchOutputFilters {
  types?: ApiResearchOutputType[] | null;
  years?: number[] | null;
  workflows?: ApiResearchOutputWorkflowOption[] | null;
}

export interface ApiResearchOutputStatus {
  current?: boolean | null;
  statusEs?: string | null;
  statusEn?: string | null;
  publicationYear?: number | null;
  publicationMonth?: number | null;
  publicationDay?: number | null;
}

export interface ApiResearchOutputIdentifier {
  typeDiscriminator?: string | null;
  idSource?: string | null;
  value?: string | null;
}

export interface ApiResearchOutputElectronicVersion {
  id?: number | null;
  pureId?: number | null;
  typeDiscriminator?: string | null;
  doi?: string | null;
  accessTypeEs?: string | null;
  accessTypeEn?: string | null;
  versionTypeEs?: string | null;
  versionTypeEn?: string | null;
}

export interface ApiResearchOutputDoiVersions {
  researchOutputId?: number;
  count?: number;
  distinctCount?: number;
  items?: ApiResearchOutputElectronicVersion[] | null;
}

export interface ApiResearchOutputLink {
  url?: string | null;
  descriptionEs?: string | null;
  descriptionEn?: string | null;
  linkTypeEs?: string | null;
  linkTypeEn?: string | null;
}

export interface ApiResearchOutputContributor {
  id: number;
  personId?: number | null;
  personUuid?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  fullName?: string | null;
  role?: string | null;
  roleEs?: string | null;
  roleEn?: string | null;
  external?: boolean | null;
  correspondingAuthor?: boolean | null;
  organizationUuids?: string[] | null;
}

export interface ApiResearchOutputKeyword {
  type?: string | null;
  value?: string | null;
  locale?: string | null;
}

export interface ApiResearchOutputKeywordGroup {
  name?: string | null;
  nameEs?: string | null;
  nameEn?: string | null;
  keywords?: ApiResearchOutputKeyword[] | null;
}

export interface ApiResearchOutputJournal {
  uuid?: string | null;
  title?: string | null;
  issn?: string | null;
  synced?: boolean | null;
}

export interface ApiResearchOutputDetail extends ApiResearchOutputSummary {
  typeEs?: string | null;
  typeEn?: string | null;
  submissionYear?: number | null;
  volume?: string | null;
  issue?: string | null;
  pages?: string | null;
  numberOfPages?: number | null;
  journalDetail?: ApiResearchOutputJournal | null;
  publicationStatuses?: ApiResearchOutputStatus[] | null;
  identifiers?: ApiResearchOutputIdentifier[] | null;
  electronicVersions?: ApiResearchOutputElectronicVersion[] | null;
  contributors?: ApiResearchOutputContributor[] | null;
  keywordGroups?: ApiResearchOutputKeywordGroup[] | null;
  links?: ApiResearchOutputLink[] | null;
  externalOrganizationUuids?: string[] | null;
  doiElectronicVersionCount?: number | null;
  distinctDoiCount?: number | null;
}

export interface ApiResearcherSummary {
  id: number;
  uuid?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  fullName?: string | null;
  email?: string | null;
  position?: string | null;
  faculty?: string | null;
  orcid?: string | null;
  orcidAuthenticated?: boolean | null;
  scopusIds?: string[] | null;
  publicationCount?: number | null;
  dataQualityStatus?: string | null;
}

export interface ApiResearcherDetail extends ApiResearcherSummary {
  gender?: string | null;
  primaryId?: string | null;
  identities?: ApiIdentifier[] | null;
  publications?: ApiResearchOutputSummary[] | null;
}

export interface ApiPersonSummary {
  id: number;
  pureId?: number | null;
  uuid?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  fullName?: string | null;
  email?: string | null;
  position?: string | null;
  orcid?: string | null;
  orcidAuthenticated?: boolean | null;
  scopusIds?: string[] | null;
  scopusIdCount?: number | null;
  publicationCount?: number | null;
  organization?: string | null;
  activeInPure?: boolean | null;
  createdBy?: string | null;
  modifiedBy?: string | null;
  scopusHindex?: number | null;
  scopusCitations?: number | null;
}

export interface ApiPersonAffiliation {
  personId?: number | null;
  pureId?: number | null;
  organizationUuid?: string | null;
  organizationId?: number | null;
  nameEs?: string | null;
  nameEn?: string | null;
  typeEs?: string | null;
  typeEn?: string | null;
  organizationActiveInPure?: boolean | null;
  resolved?: boolean | null;
  employmentType?: string | null;
  email?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  primaryAssociation?: boolean | null;
  contractType?: string | null;
  fte?: number | null;
  jobDescription?: string | null;
  jobTitle?: string | null;
  staffType?: string | null;
}

export interface ApiPersonDetail extends ApiPersonSummary {
  gender?: string | null;
  primaryId?: string | null;
  identities?: ApiIdentifier[] | null;
  affiliations?: ApiPersonAffiliation[] | null;
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

export interface ApiOrcidWorkExternalId {
  type?: string | null;
  value?: string | null;
  normalizedValue?: string | null;
  url?: string | null;
  relationship?: string | null;
}

export interface ApiOrcidWork {
  id?: number | null;
  workGroupId?: number | null;
  putCode?: number | null;
  orcid?: string | null;
  workType?: string | null;
  title?: string | null;
  journalTitle?: string | null;
  publicationYear?: number | null;
  doi?: string | null;
  eid?: string | null;
  url?: string | null;
  sourceName?: string | null;
  citationType?: string | null;
  detailLoaded?: boolean | null;
  contributorCount?: number;
  activeInOrcid?: boolean | null;
  matchStatus?: string | null;
  externalIds?: ApiOrcidWorkExternalId[] | null;
}

export interface ApiOrcidWorkGroup {
  id?: number | null;
  orcid?: string | null;
  title?: string | null;
  journalTitle?: string | null;
  workType?: string | null;
  publicationYear?: number | null;
  doi?: string | null;
  activeInOrcid?: boolean | null;
  sourceCount?: number;
  sources?: string[] | null;
  matchStatus?: string | null;
  externalIds?: ApiOrcidWorkExternalId[] | null;
  assertions?: ApiOrcidWork[] | null;
}

export interface ApiPersonOrcidWorks {
  personId?: number;
  orcid?: string | null;
  orcidValid?: boolean | null;
  orcidSyncPending?: boolean | null;
  lastOrcidSyncAt?: string | null;
  lastOrcidSyncStatus?: string | null;
  workGroupCount?: number;
  workAssertionCount?: number;
  matchedByDoi?: number;
  orcidOnly?: number;
  noDoi?: number;
  pureOnly?: number;
  ambiguous?: number;
  groups?: ApiOrcidWorkGroup[] | null;
  works?: ApiOrcidWork[] | null;
}

export interface ApiOrcidStats {
  configured?: boolean;
  tablesAvailable?: boolean;
  peopleWithOrcid?: number;
  peopleSynced?: number;
  workGroupCount?: number;
  workAssertionCount?: number;
  works?: number;
  lastProcessed?: number | null;
  lastErrors?: number | null;
  lastExecution?: string | null;
  lastStatus?: string | null;
}

export interface ApiIndicatorMeasure {
  numerator?: number;
  denominator?: number;
  percentage?: number | null;
  universe?: string | null;
}

export interface ApiResearcherIdentityIndicators {
  scopusCoverage?: ApiIndicatorMeasure;
  multipleScopusProfiles?: ApiIndicatorMeasure;
  controlledSignature?: ApiIndicatorMeasure;
  highSignatureFragmentation?: ApiIndicatorMeasure;
  normalizedUssOrcidAffiliation?: ApiIndicatorMeasure;
  orcidNormalizedCount?: number;
  orcidVariantCount?: number;
  orcidNotFoundCount?: number;
  orcidNotEvaluableCount?: number;
}

export interface ApiOrcidAdoptionMonth {
  month?: string;
  totalPersons?: number | null;
  personsWithOrcid?: number | null;
  percentage?: number | null;
  complete?: boolean;
}

export interface ApiOrcidAdoptionSeries {
  metric?: string;
  definition?: string;
  universe?: string;
  availableFrom?: string | null;
  from?: string;
  to?: string;
  months?: ApiOrcidAdoptionMonth[] | null;
}

export interface ApiResearcherIdentityCase {
  personId?: number;
  pureId?: number | null;
  firstName?: string | null;
  lastName?: string | null;
  fullName?: string | null;
  position?: string | null;
  orcid?: string | null;
  scopusDistinctCount?: number | null;
  evaluatedPublications?: number | null;
  distinctSignatureCount?: number | null;
  primarySignature?: string | null;
  classification?: string | null;
  employmentOrganizationName?: string | null;
  employmentOrganizationNames?: string[] | null;
}

export interface ApiResearcherIdentitySignatureVariant {
  displaySignature?: string | null;
  normalizedSignature?: string | null;
  publicationCount?: number | null;
}

export interface ApiResearcherIdentitySignatureDetail {
  personId?: number;
  fullName?: string | null;
  position?: string | null;
  evaluatedPublications?: number | null;
  distinctSignatureCount?: number | null;
  variants?: ApiResearcherIdentitySignatureVariant[] | null;
}

export interface ApiOrcidDiscoveryStats {
  people?: number;
  withOrcid?: number;
  withoutOrcid?: number;
  withCandidates?: number;
  strongMatches?: number;
  manualReview?: number;
  noCandidate?: number;
  conflicts?: number;
  searchMaxCandidates?: number;
}

export interface ApiOrcidDiscoveryExecution {
  id?: number;
  status?: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  startedBy?: string | null;
  totalPeople?: number;
  processedPeople?: number;
  peopleWithCandidates?: number;
  strongMatchCount?: number;
  manualReviewCount?: number;
  noCandidateCount?: number;
  tooManyCount?: number;
  conflictCount?: number;
  errorCount?: number;
}

export interface ApiOrcidCandidateEvidence {
  id?: number;
  researchOutputId?: number | null;
  doi?: string | null;
  localAuthor?: string | null;
  localPosition?: number | null;
  orcidAuthor?: string | null;
  orcidPosition?: number | null;
  nameMatch?: boolean | null;
  positionMatch?: boolean | null;
  note?: string | null;
  personFullName?: string | null;
  contributorFullName?: string | null;
  personFirstName?: string | null;
  personLastName?: string | null;
  contributorFirstName?: string | null;
  contributorLastName?: string | null;
  namePartitionDifferent?: boolean | null;
  reasonCodes?: string[] | null;
}

export interface ApiOrcidCandidate {
  id?: number;
  orcid?: string;
  score?: number;
  matchLevel?: string;
  status?: string;
  nameMatch?: boolean | null;
  doiMatchCount?: number;
  authorMatch?: boolean | null;
  positionMatch?: boolean | null;
  alreadyUsedPersonId?: number | null;
  evidence?: ApiOrcidCandidateEvidence[];
}

export interface ApiOrcidDiscoveryPerson {
  id?: number;
  personId?: number;
  pureId?: number | null;
  uuid?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  fullName?: string;
  cargo?: string | null;
  pureOrcid?: string | null;
  foundOrcid?: string | null;
  selectedOrcid?: string | null;
  bestCandidateId?: number | null;
  uniqueStrongCandidateId?: number | null;
  personStatus?: string;
  statusReason?: string | null;
  candidateCount?: number;
  searchHitCount?: number;
  doiMatchCount?: number;
  selectedCandidateId?: number | null;
  selectionOrigin?: string | null;
  selectedBy?: string | null;
  selectedAt?: string | null;
  searchGivenName?: string | null;
  searchFamilyName?: string | null;
  lastError?: string | null;
  selectable?: boolean;
  selected?: boolean;
  selectionDisabledReason?: string | null;
  candidates?: ApiOrcidCandidate[];
}

export interface ApiOrcidDiscoverySelectedItem {
  personId?: number;
  displayName?: string | null;
  pureId?: number | null;
  orcid?: string | null;
  candidateId?: number | null;
  personStatus?: string | null;
  selectionOrigin?: string | null;
  queued?: boolean;
}

export interface ApiOrcidDiscoveryApplyResult {
  selected?: number;
  succeeded?: number;
  failed?: number;
  items?: Array<{
    status?: string;
    localParentId?: number | null;
    parentUuid?: string | null;
    childValue?: string | null;
    displayName?: string | null;
    pureId?: number | null;
    message?: string | null;
  }>;
}

export interface ApiPersonOrcidSyncResult {
  personId?: number;
  orcid?: string | null;
  status?: string;
  received?: number;
  created?: number;
  updated?: number;
  unchanged?: number;
  reactivated?: number;
  markedInactive?: number;
}

export interface ApiPersonScopusIdentifier {
  id?: number | null;
  pureId?: number | null;
  value?: string | null;
  foundInScopus?: boolean | null;
  scopusAuthorId?: number | null;
  authorName?: string | null;
  numberDocuments?: number | null;
  orcid?: string | null;
}

export interface ApiPersonScopusIdentifiers {
  personId?: number;
  count?: number;
  distinctCount?: number;
  matchedCount?: number;
  items?: ApiPersonScopusIdentifier[] | null;
}

export interface ApiPersonFilters {
  positions?: string[] | null;
  withoutPositionAvailable?: boolean | null;
  scopusIdCounts?: number[] | null;
}

export interface ApiScopusCountBucket {
  count?: number | null;
  people?: number | null;
}

export interface ApiScopusStats {
  without?: number | null;
  one?: number | null;
  two?: number | null;
  threeOrMore?: number | null;
  maxCount?: number | null;
  distribution?: ApiScopusCountBucket[] | null;
}

export interface ApiPersonStats {
  total?: number | null;
  totalPeople?: number | null;
  researchers?: number | null;
  withoutPosition?: number | null;
  scopus?: ApiScopusStats | null;
  withScopusHindex?: number | null;
}

export interface ApiIntegrationStatus {
  code: string;
  status: string;
  statusReason?: string | null;
  lastExecution?: string | null;
  lastSuccessfulExecution?: string | null;
  lastFullSyncAt?: string | null;
  lastSuccessfulFullSyncAt?: string | null;
  lastSyncMode?: string | null;
  supportedSyncModes?: string[] | null;
  availableSyncModes?: string[] | null;
  hasValidBaseline?: boolean | null;
  recommendedSyncMode?: string | null;
  processed?: number | null;
  errors?: number | null;
  entities?: ApiIntegrationEntitySync[] | null;
}

export interface ApiIntegrationEntitySync {
  entity: string;
  latestExecution?: {
    syncId?: string | null;
    entity?: string | null;
    status?: string | null;
    startedAt?: string | null;
    finishedAt?: string | null;
    totalExpected?: number | null;
    processed?: number | null;
    errors?: number | null;
    syncMode?: string | null;
  } | null;
}

export interface ApiDataQualityFinding {
  id: number;
  ruleId?: number | null;
  ruleCode?: string | null;
  ruleName?: string | null;
  category?: string | null;
  entityType?: string | null;
  localRecordId?: number | null;
  pureId?: number | null;
  recordUuid?: string | null;
  recordLabel?: string | null;
  fieldName?: string | null;
  issueKind?: string | null;
  correctionMode?: string | null;
  status?: string | null;
  summary?: string | null;
  currentValue?: string | null;
  suggestedValue?: string | null;
  evidenceJson?: string | null;
  evidences?: ApiDataQualityFindingEvidence[] | null;
  description?: string | null;
  ruleText?: string | null;
  alertText?: string | null;
  firstDetectedAt?: string | null;
  lastDetectedAt?: string | null;
  resolvedAt?: string | null;
  resolvedBy?: string | null;
  resolutionNote?: string | null;
  recordRoute?: string | null;
}

export interface ApiDataQualityFindingEvidence {
  id?: number | null;
  source?: string | null;
  sourceRecordId?: string | null;
  sourceField?: string | null;
  externalValue?: string | null;
  normalizedExternalValue?: string | null;
  comparisonResult?: string | null;
  externalLastSyncAt?: string | null;
  metadataJson?: string | null;
}

export interface ApiDataQualityRule {
  id: number;
  code?: string | null;
  sourceFamily?: string | null;
  entityType?: string | null;
  fieldName?: string | null;
  name?: string | null;
  description?: string | null;
  ruleText?: string | null;
  alertText?: string | null;
  bulkProcedure?: string | null;
  category?: string | null;
  correctionMode?: string | null;
  enabled?: boolean | null;
  openFindingCount?: number | null;
}

export interface ApiDataQualityExecution {
  id: number;
  status?: string | null;
  triggerType?: string | null;
  scope?: string | null;
  entityType?: string | null;
  localRecordId?: number | null;
  syncExecutionId?: number | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  totalRecords?: number | null;
  evaluatedRecords?: number | null;
  findingCount?: number | null;
  resolvedCount?: number | null;
  errorCount?: number | null;
  startedBy?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  durationSeconds?: number | null;
}

export interface ApiDataQualityStats {
  openFindings?: number | null;
  affectedRecords?: number | null;
  correctionAvailable?: number | null;
  manualReview?: number | null;
  viewOnly?: number | null;
  rulesWithFindings?: number | null;
  ruleCounts?: ApiDataQualityRuleCount[] | null;
}

export interface ApiDataQualityRuleCount {
  code?: string | null;
  name?: string | null;
  sourceFamily?: string | null;
  count?: number | null;
}

export interface ApiDataQualityApplyResult {
  applied?: number | null;
  remoteChanged?: number | null;
  localFailed?: number | null;
  reviewRequired?: number | null;
  failed?: number | null;
  items?: ApiDataQualityApplyItem[] | null;
}

export interface ApiDataQualityApplyItem {
  findingId?: number | null;
  ruleCode?: string | null;
  recordLabel?: string | null;
  status?: string | null;
  message?: string | null;
  currentValue?: string | null;
  suggestedValue?: string | null;
}
