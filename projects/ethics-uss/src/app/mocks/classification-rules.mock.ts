import type { ClassificationCode } from '../core/models/ethics.models';
import { NEW_PROJECT_FORM } from '../core/forms/new-project-form.definition';
import { FormOption, optionsOf } from '../core/forms/form.types';

export interface StudyObjectOption {
  id: string;
  label: string;
  committee: ClassificationCode;
  group: 'CEC' | 'CECUA' | 'BIOSEGURIDAD' | 'NONE';
}

export const STUDY_OBJECT_OPTIONS: StudyObjectOption[] = optionsOf(NEW_PROJECT_FORM, 'NP-024').map(toStudyObject);

export function applyNoneExclusive(selected: string[], toggled: string, exclusiveId = 'NONE'): string[] {
  if (toggled === exclusiveId) {
    return selected.includes(exclusiveId) ? [] : [exclusiveId];
  }
  const withoutExclusive = selected.filter((id) => id !== exclusiveId && id !== toggled);
  return selected.includes(toggled) ? withoutExclusive : [...withoutExclusive, toggled];
}

export function classifyStudyObjects(selected: string[]): ClassificationCode[] {
  if (selected.length === 0) {
    return [];
  }
  if (selected.includes('NONE')) {
    return ['NO_REQUIERE_COMITE'];
  }
  const codes = new Set<ClassificationCode>();
  for (const id of selected) {
    const option = STUDY_OBJECT_OPTIONS.find((item) => item.id === id);
    if (option && option.committee !== 'NO_REQUIERE_COMITE') {
      codes.add(option.committee);
    }
  }
  return [...codes];
}

export const REQUIRED_DOCUMENTS: Record<string, Array<{ key: string; label: string }>> = {
  CEC: [
    { key: 'protocolo', label: 'Protocolo de investigación' },
    { key: 'consentimiento', label: 'Consentimiento informado' },
    { key: 'instrumentos', label: 'Instrumentos de recolección' },
    { key: 'carta-ir', label: 'Carta del investigador responsable' },
    { key: 'autorizacion', label: 'Autorización institucional' }
  ],
  CECUA: [
    { key: 'protocolo-animal', label: 'Protocolo de uso de animales' },
    { key: 'justificacion', label: 'Justificación científica' },
    { key: 'bienestar', label: 'Plan de bienestar animal' },
    { key: 'capacitacion', label: 'Constancia de capacitación del equipo' },
    { key: 'instalaciones', label: 'Descripción de instalaciones' }
  ],
  BIOSEGURIDAD: [
    { key: 'protocolo-bio', label: 'Protocolo de bioseguridad' },
    { key: 'fichas', label: 'Fichas de seguridad de reactivos' },
    { key: 'residuos', label: 'Plan de manejo de residuos' },
    { key: 'lab', label: 'Autorización de laboratorio' }
  ]
};

function toStudyObject(option: FormOption): StudyObjectOption {
  return {
    id: option.id,
    label: option.label,
    committee: option.classification ?? 'NO_REQUIERE_COMITE',
    group: option.group ?? 'NONE'
  };
}
