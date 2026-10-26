import { DocumentMetadata, EthicsCommittee } from '../models/ethics.models';

export const VALIDATION_DRAFT_OWNER = 'draft-validation';
export const VALIDATION_LETTER_TYPE = 'validation-letter';

export const VALIDATION_COMMITTEE_ORDER: EthicsCommittee[] = ['CEC', 'CECUA', 'BIOSEGURIDAD'];

export const VALIDATION_COMMITTEE_DOCUMENTS = [
  { id: 'acta', labelKey: 'validation.docs.acta', required: true },
  { id: 'protocolo', labelKey: 'validation.docs.protocolo', required: true },
  { id: 'adjudicacion', labelKey: 'validation.docs.adjudicacion', required: false },
  { id: 'seguimiento', labelKey: 'validation.docs.seguimiento', required: false },
  { id: 'enmiendas', labelKey: 'validation.docs.enmiendas', required: false },
  { id: 'otros', labelKey: 'validation.docs.otros', required: false }
] as const;

export type ValidationCommitteeDocumentType = (typeof VALIDATION_COMMITTEE_DOCUMENTS)[number]['id'];

export function isValidationCommittee(value: string): value is EthicsCommittee {
  return value === 'CEC' || value === 'CECUA' || value === 'BIOSEGURIDAD';
}

export function isValidationLetter(doc: DocumentMetadata): boolean {
  return !doc.committeeType && (
    doc.documentType === VALIDATION_LETTER_TYPE
    || doc.requirementKey === VALIDATION_LETTER_TYPE
    || doc.requirementKey === 'carta'
  );
}

export function validationDocumentLabelKey(doc: DocumentMetadata): string | null {
  if (isValidationLetter(doc)) {
    return 'validation.letter';
  }
  const type = doc.documentType ?? doc.requirementKey?.split(':').at(-1);
  return VALIDATION_COMMITTEE_DOCUMENTS.find((item) => item.id === type)?.labelKey ?? null;
}

export function latestValidationDocument(docs: DocumentMetadata[], documentType: string, committeeType?: EthicsCommittee): DocumentMetadata | undefined {
  return docs
    .filter((item) => item.documentType === documentType && item.committeeType === committeeType)
    .sort((a, b) => b.version - a.version)[0];
}
