/** Tipo documental del Plan de Gestión de Datos. La lógica usa este id, no la etiqueta traducida ni el nombre del archivo. */
export const DATA_MANAGEMENT_PLAN = 'DATA_MANAGEMENT_PLAN';

export function isDataManagementPlan(documentType: string | undefined | null): boolean {
  return documentType === DATA_MANAGEMENT_PLAN;
}

export function planVersions<T extends { documentType?: string; version: number; uploadedAt?: string }>(rows: readonly T[]): T[] {
  return rows
    .filter((row) => isDataManagementPlan(row.documentType))
    .sort((a, b) => b.version - a.version || (b.uploadedAt ?? '').localeCompare(a.uploadedAt ?? ''));
}

export function currentPlan<T extends { documentType?: string; version: number; uploadedAt?: string }>(rows: readonly T[]): T | null {
  return planVersions(rows)[0] ?? null;
}

export interface PlanAttachment {
  documentType: typeof DATA_MANAGEMENT_PLAN;
  requirementKey: typeof DATA_MANAGEMENT_PLAN;
  version: number;
  fileName: string;
}

/** El archivo entra al único documento lógico. El nombre se conserva y no elige el tipo. */
export function planAttach<T extends { documentType?: string; version: number; uploadedAt?: string }>(
  existing: readonly T[],
  fileName: string
): PlanAttachment {
  const current = currentPlan(existing);
  return {
    documentType: DATA_MANAGEMENT_PLAN,
    requirementKey: DATA_MANAGEMENT_PLAN,
    version: (current?.version ?? 0) + 1,
    fileName
  };
}

export interface GenericAttachment {
  documentType?: undefined;
  requirementKey: string;
  version: number;
  fileName: string;
}

/** Un adjunto genérico no recibe tipo, aunque el nombre parezca un plan. */
export function genericAttach(fileName: string, requirementKey: string): GenericAttachment {
  return {
    requirementKey,
    version: 1,
    fileName
  };
}
