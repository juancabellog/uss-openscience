import { I18nService } from '../i18n/i18n.service';
import { SyncChangeLine } from '../services/api/sync-api.service';

export interface PresentedSyncChange {
  kind: 'message' | 'field' | 'added' | 'removed' | 'organization-context';
  title: string;
  subtitle?: string | null;
  organizationKey?: string | null;
  organizationName?: string | null;
  organizationUuid?: string | null;
  oldValue?: string | null;
  newValue?: string | null;
}

const FIELD_KEYS: Record<string, string> = {
  cargo: 'sync.detail.fields.position',
  email: 'sync.detail.fields.email',
  firstName: 'sync.detail.fields.firstName',
  lastName: 'sync.detail.fields.lastName',
  orcid: 'sync.detail.fields.orcid',
  orcidAuthenticated: 'sync.detail.fields.orcidAuthenticated',
  titulo: 'sync.detail.fields.title',
  fechaIngreso: 'sync.detail.fields.startDate',
  fechaCreacionPure: 'sync.detail.fields.pureCreated',
  fechaModificacionPure: 'sync.detail.fields.pureModified',
  title: 'sync.detail.fields.outputTitle',
  issn: 'sync.detail.fields.issn',
  modifiedDate: 'sync.detail.fields.pureModified',
  createdDate: 'sync.detail.fields.pureCreated',
  peerReview: 'sync.detail.fields.peerReview',
  volume: 'sync.detail.fields.volume',
  pages: 'sync.detail.fields.pages',
  journalNumber: 'sync.detail.fields.journalNumber',
  numberOfPages: 'sync.detail.fields.numberOfPages',
  submissionYear: 'sync.detail.fields.submissionYear',
  workflowStep: 'sync.detail.fields.workflowStep',
  workflowEs: 'sync.detail.fields.workflowDescriptionEs',
  workflowEn: 'sync.detail.fields.workflowDescriptionEn',
  portalUrl: 'sync.detail.fields.portalUrl',
  employmentType: 'sync.detail.fields.employmentType',
  startDate: 'sync.detail.fields.orgStartDate',
  endDate: 'sync.detail.fields.orgEndDate',
  primaryAssociation: 'sync.detail.fields.primaryAssociation',
  contractType: 'sync.detail.fields.contractType',
  fte: 'sync.detail.fields.fte',
  jobDescription: 'sync.detail.fields.jobDescription',
  jobTitle: 'sync.detail.fields.jobTitle',
  staffType: 'sync.detail.fields.staffType',
  publisherUuid: 'sync.detail.fields.publisherUuid',
  publisherSystemName: 'sync.detail.fields.publisherSystemName',
  typeUri: 'sync.detail.fields.typeUri',
  typeEn: 'sync.detail.fields.typeEn',
  typeEs: 'sync.detail.fields.typeEs',
  workflowDescriptionEn: 'sync.detail.fields.workflowDescriptionEn',
  workflowDescriptionEs: 'sync.detail.fields.workflowDescriptionEs',
  systemName: 'sync.detail.fields.systemName',
  createdBy: 'sync.detail.fields.createdBy',
  modifiedBy: 'sync.detail.fields.modifiedBy',
  version: 'sync.detail.fields.version',
  identifierValue: 'sync.detail.fields.identifierValue',
  identifierType: 'sync.detail.fields.identifierType',
  keywordGroupName: 'sync.detail.fields.keywordGroupName',
  classificationTerm: 'sync.detail.fields.classificationTerm',
  keywordUri: 'sync.detail.fields.keywordUri',
  createdDatePure: 'sync.detail.fields.pureCreated',
  modifiedDatePure: 'sync.detail.fields.pureModified',
  pureId: 'common.pureId'
};

const LIFECYCLE = new Set(['Record created', 'Returned to PURE', 'No longer present in PURE']);

export function presentSyncChange(change: SyncChangeLine, i18n: I18nService): PresentedSyncChange | null {
  const description = change.description?.trim() ?? '';
  if (LIFECYCLE.has(description) && change.scope === 'PARENT') {
    return null;
  }
  const dash = i18n.translate('common.dash');
  const oldValue = blankToDash(change.oldValue, dash);
  const newValue = blankToDash(change.newValue, dash);
  const added = hasValue(change.newValue) && !hasValue(change.oldValue);
  const removed = hasValue(change.oldValue) && !hasValue(change.newValue);
  const value = hasValue(change.oldValue) ? change.oldValue : change.newValue;

  if (change.scope === 'IDENTIFIER') {
    const type = identifierType(description);
    const scopus = (type ?? description).toLowerCase().includes('scopus');
    if (scopus) {
      const key = removed ? 'sync.detail.lines.scopusRemoved' : 'sync.detail.lines.scopusAdded';
      return { kind: removed ? 'removed' : 'added', title: i18n.translate(key, { value: value ?? dash }) };
    }
    if (type && type.toLowerCase() !== 'identifier') {
      const key = removed ? 'sync.detail.lines.identifierTypedRemoved' : 'sync.detail.lines.identifierTypedAdded';
      return {
        kind: removed ? 'removed' : 'added',
        title: i18n.translate(key, { type, value: value ?? dash })
      };
    }
    const key = removed ? 'sync.detail.lines.identifierRemoved' : 'sync.detail.lines.identifierAdded';
    return { kind: removed ? 'removed' : 'added', title: i18n.translate(key, { value: value ?? dash }) };
  }
  if (change.scope === 'ELECTRONIC_VERSION') {
    if (!hasValue(value)) {
      return { kind: 'message', title: i18n.translate('sync.detail.generic.doi') };
    }
    const key = removed ? 'sync.detail.lines.doiRemoved' : 'sync.detail.lines.doiAdded';
    return { kind: removed ? 'removed' : 'added', title: i18n.translate(key, { value }) };
  }
  if (change.scope === 'CONTRIBUTOR') {
    if (!hasValue(value)) {
      return { kind: 'message', title: i18n.translate('sync.detail.generic.contributors') };
    }
    const key = removed ? 'sync.detail.lines.contributorRemoved' : 'sync.detail.lines.contributorAdded';
    return { kind: removed ? 'removed' : 'added', title: i18n.translate(key, { value }) };
  }
  if (change.scope === 'JOURNAL') {
    if (added || removed) {
      const key = removed ? 'sync.detail.lines.journalRemoved' : 'sync.detail.lines.journalAdded';
      return { kind: removed ? 'removed' : 'added', title: i18n.translate(key, { value: value ?? dash }) };
    }
    if (description && FIELD_KEYS[description]) {
      return {
        kind: 'field',
        title: i18n.translate(FIELD_KEYS[description]),
        oldValue,
        newValue
      };
    }
    return {
      kind: 'field',
      title: i18n.translate('sync.detail.lines.journalChanged'),
      oldValue,
      newValue
    };
  }
  if (change.scope === 'KEYWORD') {
    if (!hasValue(value)) {
      return { kind: 'message', title: i18n.translate('sync.detail.generic.keywords') };
    }
    const key = removed ? 'sync.detail.lines.keywordRemoved' : 'sync.detail.lines.keywordAdded';
    return { kind: removed ? 'removed' : 'added', title: i18n.translate(key, { value }) };
  }
  if (change.scope === 'ORGANIZATION') {
    if (added || removed) {
      const key = removed ? 'sync.detail.lines.organizationRemoved' : 'sync.detail.lines.organizationAdded';
      return { kind: removed ? 'removed' : 'added', title: i18n.translate(key, { value: value ?? dash }) };
    }
    const org = parseOrganizationField(description);
    return {
      kind: 'field',
      title: FIELD_KEYS[org.field]
        ? i18n.translate(FIELD_KEYS[org.field])
        : org.field || i18n.translate('sync.detail.lines.organizationUpdated'),
      organizationKey: org.uuid || org.name || null,
      organizationName: org.name,
      organizationUuid: org.uuid,
      oldValue,
      newValue
    };
  }
  if (change.scope === 'TITLE') {
    const key = removed ? 'sync.detail.lines.titleRemoved' : 'sync.detail.lines.titleAdded';
    return { kind: removed ? 'removed' : 'added', title: i18n.translate(key, { value: value ?? dash }) };
  }
  if (change.scope === 'ISSN') {
    const key = removed ? 'sync.detail.lines.issnRemoved' : 'sync.detail.lines.issnAdded';
    return { kind: removed ? 'removed' : 'added', title: i18n.translate(key, { value: value ?? dash }) };
  }
  if (change.scope === 'CLASSIFICATION') {
    const key = removed ? 'sync.detail.lines.classificationRemoved' : 'sync.detail.lines.classificationAdded';
    return { kind: removed ? 'removed' : 'added', title: i18n.translate(key, { value: value ?? dash }) };
  }
  if (change.scope === 'KEYWORD_GROUP') {
    const key = removed ? 'sync.detail.lines.keywordGroupRemoved' : 'sync.detail.lines.keywordGroupAdded';
    return { kind: removed ? 'removed' : 'added', title: i18n.translate(key, { value: value ?? dash }) };
  }
  if (change.scope === 'LINK' || change.scope === 'STATUS' || change.scope === 'EXTERNAL_ORGANIZATION') {
    if (added || removed) {
      return {
        kind: removed ? 'removed' : 'added',
        title: i18n.translate(scopeFallbackKey(change.scope, removed), { value: value ?? dash })
      };
    }
    return { kind: 'message', title: i18n.translate(scopeGenericKey(change.scope)) };
  }
  if (change.scope === 'PARENT' && description && FIELD_KEYS[description]) {
    return {
      kind: 'field',
      title: i18n.translate(FIELD_KEYS[description]),
      oldValue,
      newValue
    };
  }
  if (description && (added || removed || (hasValue(change.oldValue) && hasValue(change.newValue)))) {
    return {
      kind: added ? 'added' : removed ? 'removed' : 'field',
      title: FIELD_KEYS[description] ? i18n.translate(FIELD_KEYS[description]) : description,
      oldValue: added || removed ? undefined : oldValue,
      newValue: added || removed ? undefined : newValue
    };
  }
  if (description) {
    return { kind: 'message', title: description };
  }
  return { kind: 'message', title: i18n.translate(scopeGenericKey(change.scope)) };
}

function identifierType(description: string): string | null {
  const match = /^(.*?)\s+(added|removed)$/i.exec(description.trim());
  const type = match?.[1]?.trim();
  return type ? type : null;
}

function scopeFallbackKey(scope: string | undefined, removed: boolean): string {
  if (scope === 'LINK') {
    return removed ? 'sync.detail.lines.linkRemoved' : 'sync.detail.lines.linkAdded';
  }
  if (scope === 'STATUS') {
    return removed ? 'sync.detail.lines.statusRemoved' : 'sync.detail.lines.statusAdded';
  }
  return removed ? 'sync.detail.lines.externalOrgRemoved' : 'sync.detail.lines.externalOrgAdded';
}

function scopeGenericKey(scope?: string | null): string {
  switch (scope) {
    case 'CONTRIBUTOR':
      return 'sync.detail.generic.contributors';
    case 'ELECTRONIC_VERSION':
      return 'sync.detail.generic.doi';
    case 'IDENTIFIER':
      return 'sync.detail.generic.identifiers';
    case 'JOURNAL':
      return 'sync.detail.generic.journal';
    case 'ISSN':
      return 'sync.detail.generic.issn';
    case 'CLASSIFICATION':
      return 'sync.detail.generic.classifications';
    case 'KEYWORD_GROUP':
      return 'sync.detail.generic.keywordGroups';
    case 'KEYWORD':
      return 'sync.detail.generic.keywords';
    case 'ORGANIZATION':
      return 'sync.detail.generic.organizations';
    default:
      return 'sync.detail.generic.changed';
  }
}

export function withOrganizationContext(
  lines: PresentedSyncChange[],
  i18n: I18nService
): PresentedSyncChange[] {
  const result: PresentedSyncChange[] = [];
  let lastKey: string | null = null;
  for (const line of lines) {
    if (line.kind === 'field' && line.organizationKey && line.organizationKey !== lastKey) {
      result.push({
        kind: 'organization-context',
        title: line.organizationName || i18n.translate('sync.detail.generic.organizationFallback'),
        subtitle: line.organizationUuid
          ? i18n.translate('sync.detail.lines.organizationUuid', { value: line.organizationUuid })
          : null,
        organizationKey: line.organizationKey
      });
      lastKey = line.organizationKey;
    }
    result.push(line);
  }
  return result;
}

function parseOrganizationField(description: string): { field: string; uuid: string | null; name: string | null } {
  if (!description.includes('||')) {
    return { field: description, uuid: null, name: null };
  }
  const parts = description.split('||');
  const field = parts[parts.length - 1]?.trim() || description;
  const uuid = parts[0]?.trim() || null;
  const name = parts.length > 2 ? parts[1]?.trim() || null : null;
  return { field, uuid, name };
}

function hasValue(value?: string | null): value is string {
  return value != null && value.trim() !== '';
}

function blankToDash(value: string | null | undefined, dash: string): string {
  return hasValue(value) ? String(value) : dash;
}
