export interface PersonSnapshot {
  capturedAt: string;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  campus: string | null;
  faculty: string | null;
  program: string | null;
  position: string | null;
}

function text(payload: Record<string, unknown>, key: string): string | null {
  const value = payload[key];
  if (value == null) {
    return null;
  }
  const trimmed = String(value).trim();
  return trimmed ? trimmed : null;
}

/** Copia los datos de la persona tal como quedaron en el envío. No se toma en el borrador. */
export function capturePersonSnapshot(payload: Record<string, unknown>, capturedAt: string): PersonSnapshot {
  return {
    capturedAt,
    firstName: text(payload, 'irName'),
    lastName: text(payload, 'irLastName'),
    email: text(payload, 'irEmail'),
    campus: text(payload, 'campus'),
    faculty: text(payload, 'faculty'),
    program: text(payload, 'program'),
    position: text(payload, 'irPosition')
  };
}
