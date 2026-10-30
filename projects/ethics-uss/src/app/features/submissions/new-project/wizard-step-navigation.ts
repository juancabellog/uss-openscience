export type WizardStepStatus = 'current' | 'available' | 'locked';
export type WizardMove = 'stay' | 'back' | 'forward' | 'blocked';

export interface WizardStepField {
  required: boolean;
  visible: boolean;
  value: unknown;
  valid: boolean;
}

/** Un paso está completo cuando cada pregunta visible y obligatoria tiene respuesta válida. */
export function sectionComplete(fields: readonly WizardStepField[]): boolean {
  return fields.every((field) => !field.required || !field.visible || (field.valid && answered(field.value)));
}

/**
 * Hacia atrás no se valida. Hacia adelante exige que todos los pasos anteriores al destino estén completos.
 * El paso actual no se mueve.
 */
export function wizardMove(current: number, target: number, complete: readonly boolean[]): WizardMove {
  if (target === current || target < 1 || target > complete.length) {
    return 'stay';
  }
  if (target < current) {
    return 'back';
  }
  return complete.slice(0, target - 1).every(Boolean) ? 'forward' : 'blocked';
}

export function wizardStepStatus(current: number, target: number, complete: readonly boolean[]): WizardStepStatus {
  if (target === current) {
    return 'current';
  }
  return wizardMove(current, target, complete) === 'blocked' ? 'locked' : 'available';
}

export function currentStepBlocks(current: number, complete: readonly boolean[]): boolean {
  return complete[current - 1] !== true;
}

function answered(value: unknown): boolean {
  if (value === null || value === undefined) {
    return false;
  }
  if (typeof value === 'string') {
    return value.trim().length > 0;
  }
  if (typeof value === 'boolean') {
    return value;
  }
  if (Array.isArray(value)) {
    return value.length > 0;
  }
  return typeof value === 'object' && 'name' in value && typeof value.name === 'string' && value.name.length > 0;
}
