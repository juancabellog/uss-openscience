import { EthicsCommittee } from '../core/models/ethics.models';

export interface CommitteeDocumentRequirement {
  id: string;
  committeeType: EthicsCommittee;
  nameKey: string;
  required: boolean;
  versionable: boolean;
  active: boolean;
}

export const COMMITTEE_DOCUMENT_REQUIREMENTS: CommitteeDocumentRequirement[] = [
  req('CEC', 'cec-letter-president', 'catalogs.docs.cecLetterPresident', true),
  req('CEC', 'cec-commitment', 'catalogs.docs.cecCommitment', true),
  req('CEC', 'cec-protocol', 'catalogs.docs.cecProtocol', true),
  req('CEC', 'cec-summary', 'catalogs.docs.cecSummary', true),
  req('CEC', 'cec-ethics-analysis', 'catalogs.docs.cecEthicsAnalysis', true),
  req('CEC', 'cec-cv', 'catalogs.docs.cecCv', true),
  req('CEC', 'cec-support-letter', 'catalogs.docs.cecSupportLetter', true),
  req('CEC', 'cec-consent', 'catalogs.docs.cecConsent', false),
  req('CEC', 'cec-assent', 'catalogs.docs.cecAssent', false),
  req('CEC', 'cec-bio-consent', 'catalogs.docs.cecBioConsent', false),
  req('CEC', 'cec-instruments', 'catalogs.docs.cecInstruments', false),
  req('CEC', 'cec-recruitment', 'catalogs.docs.cecRecruitment', false),
  req('CEC', 'cec-other-cec', 'catalogs.docs.cecOtherCec', false),
  req('CEC', 'cec-director-letter', 'catalogs.docs.cecDirectorLetter', false),
  req('CEC', 'cec-scientific-approval', 'catalogs.docs.cecScientificApproval', false),
  req('CECUA', 'cecua-protocol', 'catalogs.docs.cecuaProtocol', true),
  req('CECUA', 'cecua-summary', 'catalogs.docs.cecuaSummary', true),
  req('CECUA', 'cecua-animal-protocol', 'catalogs.docs.cecuaAnimalProtocol', true),
  req('CECUA', 'cecua-supervision', 'catalogs.docs.cecuaSupervision', true),
  req('CECUA', 'cecua-record', 'catalogs.docs.cecuaRecord', true),
  req('CECUA', 'cecua-scientific', 'catalogs.docs.cecuaScientific', false),
  req('CECUA', 'cecua-feasibility', 'catalogs.docs.cecuaFeasibility', false),
  req('BIOSEGURIDAD', 'bio-protocol', 'catalogs.docs.bioProtocol', true),
  req('BIOSEGURIDAD', 'bio-summary', 'catalogs.docs.bioSummary', true),
  req('BIOSEGURIDAD', 'bio-biosafety', 'catalogs.docs.bioBiosafety', true),
  req('BIOSEGURIDAD', 'bio-scientific', 'catalogs.docs.bioScientific', false)
];

export function requirementsFor(committee: EthicsCommittee, required?: boolean): CommitteeDocumentRequirement[] {
  return COMMITTEE_DOCUMENT_REQUIREMENTS.filter((item) =>
    item.active && item.committeeType === committee && (required === undefined || item.required === required)
  );
}

function req(committeeType: EthicsCommittee, id: string, nameKey: string, required: boolean): CommitteeDocumentRequirement {
  return { id, committeeType, nameKey, required, versionable: true, active: true };
}
