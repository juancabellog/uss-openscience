import { EthicsCommittee, ReviewFormDefinitionId } from '../models/ethics.models';

/**
 * Formularios oficiales por comité todavía no fueron entregados.
 * Estos identificadores dejan el punto de extensión; los campos visibles siguen siendo los provisionales del prototipo.
 */
export interface ReviewFormDefinition {
  id: ReviewFormDefinitionId;
  committeeType: EthicsCommittee;
  provisional: true;
  fields: Array<'metodologia' | 'riesgos' | 'observaciones'>;
}

export const REVIEW_FORM_DEFINITIONS: ReviewFormDefinition[] = [
  { id: 'CEC_REVIEW_V1', committeeType: 'CEC', provisional: true, fields: ['metodologia', 'riesgos', 'observaciones'] },
  { id: 'CECUA_REVIEW_V1', committeeType: 'CECUA', provisional: true, fields: ['metodologia', 'riesgos', 'observaciones'] },
  { id: 'BIOSECURITY_REVIEW_V1', committeeType: 'BIOSEGURIDAD', provisional: true, fields: ['metodologia', 'riesgos', 'observaciones'] }
];

export function reviewFormFor(committee: EthicsCommittee): ReviewFormDefinition {
  return REVIEW_FORM_DEFINITIONS.find((item) => item.committeeType === committee) ?? REVIEW_FORM_DEFINITIONS[0];
}
