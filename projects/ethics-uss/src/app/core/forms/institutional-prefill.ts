export interface InstitutionalProfile {
  givenName?: string | null;
  familyName?: string | null;
  email?: string | null;
  campus?: string | null;
  faculty?: string | null;
  program?: string | null;
  position?: string | null;
}

export interface PrefillField {
  value?: string | null;
  dirty: boolean;
  current: string;
  allowed?: readonly string[];
}

/** Solo completa un campo vacío que el usuario todavía no editó. */
export function prefillValue(field: PrefillField): string | null {
  const value = field.value?.trim() ?? '';
  if (!value || field.dirty || field.current.trim()) {
    return null;
  }
  if (field.allowed && !field.allowed.includes(value)) {
    return null;
  }
  return value;
}
