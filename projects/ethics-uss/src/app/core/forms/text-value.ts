const TEXTUAL = new Set(['text', 'email', 'date', 'select']);

/** Un texto ausente queda vacío. No usa String(value): String(null) es la palabra "null". */
export function asText(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

/** Conserva un string, incluso vacío. Null y undefined usan el respaldo. */
export function asTextOr(value: unknown, fallback: string): string {
  return typeof value === 'string' ? value : fallback;
}

export function isTextualType(type: string): boolean {
  return TEXTUAL.has(type);
}

export function normalizeTextFields(
  questions: ReadonlyArray<{ fieldKey: string; type: string }>,
  payload: Record<string, unknown>
): Record<string, unknown> {
  const next: Record<string, unknown> = { ...payload };
  for (const question of questions) {
    if (isTextualType(question.type)) {
      next[question.fieldKey] = asText(payload[question.fieldKey]);
    }
  }
  return next;
}
