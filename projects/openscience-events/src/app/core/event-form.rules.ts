export const EVENT_TITLE_MAX = 255;
export const EVENT_DESCRIPTION_MAX = 20000;
export const EVENT_LOCATION_MAX = 255;
export const EVENT_CAMPUS_MAX = 128;
export const EVENT_UNIT_MAX = 255;
export const EVENT_TIMEZONE_MAX = 64;
export const EVENT_VIRTUAL_ACCESS_MAX = 1000;
export const EVENT_SLUG_MAX = 80;
export const EVENT_DEFAULT_TIMEZONE = 'America/Santiago';
export const EVENT_DEFAULT_BLANK_ROWS = 10;
export const EVENT_CAMPUS_OTHER = '__OTHER__';
export const EVENT_UNIT_OTHER = 'OTHER';

const EVENT_TYPES = new Set(['TALK', 'SEMINAR', 'WORK_TABLE', 'WORKSHOP', 'JOURNEY', 'OTHER']);
const EVENT_MODALITIES = new Set(['PRESENTIAL', 'VIRTUAL', 'HYBRID']);

export interface EventFormValues {
  title: string;
  description?: string | null;
  eventType?: string | null;
  startsAt?: string | null;
  endsAt?: string | null;
  timezone?: string | null;
  modality?: string | null;
  locationName?: string | null;
  campusName?: string | null;
  virtualAccess?: string | null;
  organizingUnit?: string | null;
  organizingUnitCode?: string | null;
  registrationOpensAt?: string | null;
  registrationClosesAt?: string | null;
  allowRegistrationDuringEvent?: boolean;
  capacity?: number | string | null;
  blankSignatureRows?: number | string | null;
  publicSlug?: string | null;
}

export interface EventFieldIssue {
  field: string;
  code: string;
  message: string;
}

export const EVENT_ISSUE_FIELDS: Record<string, string> = {
  EVENT_REQUIRED: 'title',
  EVENT_TITLE_REQUIRED: 'title',
  EVENT_TITLE_TOO_LONG: 'title',
  EVENT_DESCRIPTION_REQUIRED: 'description',
  EVENT_DESCRIPTION_TOO_LONG: 'description',
  EVENT_TYPE_REQUIRED: 'eventType',
  EVENT_TYPE_INVALID: 'eventType',
  EVENT_MODALITY_REQUIRED: 'modality',
  EVENT_MODALITY_INVALID: 'modality',
  EVENT_START_REQUIRED: 'startsAt',
  EVENT_END_REQUIRED: 'endsAt',
  EVENT_ENDS_BEFORE_START: 'endsAt',
  EVENT_TIMEZONE_REQUIRED: 'timezone',
  EVENT_TIMEZONE_TOO_LONG: 'timezone',
  EVENT_UNIT_REQUIRED: 'organizingUnitCode',
  EVENT_UNIT_INVALID: 'organizingUnitCode',
  EVENT_UNIT_OTHER_REQUIRED: 'organizingUnit',
  EVENT_UNIT_TOO_LONG: 'organizingUnit',
  EVENT_LOCATION_TOO_LONG: 'locationName',
  EVENT_PLACE_REQUIRED: 'locationName',
  EVENT_CAMPUS_TOO_LONG: 'campusName',
  EVENT_VIRTUAL_ACCESS_REQUIRED: 'virtualAccess',
  EVENT_VIRTUAL_ACCESS_TOO_LONG: 'virtualAccess',
  EVENT_REGISTRATION_OPEN_REQUIRED: 'registrationOpensAt',
  EVENT_REGISTRATION_CLOSE_REQUIRED: 'registrationClosesAt',
  EVENT_REGISTRATION_WINDOW_INVALID: 'registrationClosesAt',
  EVENT_REGISTRATION_AFTER_START: 'registrationClosesAt',
  EVENT_CAPACITY_INVALID: 'capacity',
  EVENT_BLANK_ROWS_INVALID: 'blankSignatureRows',
  EVENT_SLUG_TOO_LONG: 'publicSlug',
  EVENT_SLUG_TAKEN: 'publicSlug'
};

export function collectEventIssues(body: EventFormValues | null, publish: boolean): EventFieldIssue[] {
  const issues: EventFieldIssue[] = [];
  if (body == null) {
    issues.push({ field: 'title', code: 'EVENT_REQUIRED', message: 'El evento es obligatorio' });
    return issues;
  }
  add(issues, titleIssue(body.title));
  add(issues, lengthIssue('description', body.description, EVENT_DESCRIPTION_MAX, 'EVENT_DESCRIPTION_TOO_LONG'));
  add(issues, lengthIssue('locationName', body.locationName, EVENT_LOCATION_MAX, 'EVENT_LOCATION_TOO_LONG'));
  add(issues, lengthIssue('campusName', body.campusName, EVENT_CAMPUS_MAX, 'EVENT_CAMPUS_TOO_LONG'));
  add(issues, lengthIssue('organizingUnit', body.organizingUnit, EVENT_UNIT_MAX, 'EVENT_UNIT_TOO_LONG'));
  add(issues, unitIssue(body, publish));
  add(issues, lengthIssue('timezone', body.timezone, EVENT_TIMEZONE_MAX, 'EVENT_TIMEZONE_TOO_LONG'));
  add(issues, lengthIssue('virtualAccess', body.virtualAccess, EVENT_VIRTUAL_ACCESS_MAX, 'EVENT_VIRTUAL_ACCESS_TOO_LONG'));
  add(issues, lengthIssue('publicSlug', body.publicSlug, EVENT_SLUG_MAX, 'EVENT_SLUG_TOO_LONG'));
  if (trimToNull(body.eventType) && !parseType(body.eventType)) {
    issues.push({ field: 'eventType', code: 'EVENT_TYPE_INVALID', message: 'Tipo de evento no válido' });
  }
  if (trimToNull(body.modality) && !parseModality(body.modality)) {
    issues.push({ field: 'modality', code: 'EVENT_MODALITY_INVALID', message: 'Modalidad no válida' });
  }
  add(issues, capacityIssue(body.capacity == null ? null : String(body.capacity)));
  add(issues, blankRowsIssue(body.blankSignatureRows == null || body.blankSignatureRows === '' ? null : String(body.blankSignatureRows)));
  const starts = parseDate(body.startsAt);
  const ends = parseDate(body.endsAt);
  const opens = parseDate(body.registrationOpensAt);
  const closes = parseDate(body.registrationClosesAt);
  issues.push(...windowIssues(starts, ends, opens, closes, !!body.allowRegistrationDuringEvent));
  if (publish) {
    issues.push(...publishIssues(body, starts, ends, opens, closes));
  }
  return issues;
}

export function trimToNull(raw?: string | null): string | null {
  if (raw == null) {
    return null;
  }
  const value = String(raw).trim();
  return value.length === 0 ? null : value;
}

function publishIssues(
  body: EventFormValues,
  starts: Date | null,
  ends: Date | null,
  opens: Date | null,
  closes: Date | null
): EventFieldIssue[] {
  const issues: EventFieldIssue[] = [];
  if (trimToNull(body.description) == null) {
    issues.push({ field: 'description', code: 'EVENT_DESCRIPTION_REQUIRED', message: 'La descripción es obligatoria para publicar' });
  }
  if (parseType(body.eventType) == null) {
    issues.push({ field: 'eventType', code: 'EVENT_TYPE_REQUIRED', message: 'El tipo de evento es obligatorio para publicar' });
  }
  const modality = parseModality(body.modality);
  if (modality == null) {
    issues.push({ field: 'modality', code: 'EVENT_MODALITY_REQUIRED', message: 'La modalidad es obligatoria para publicar' });
  }
  if (starts == null) {
    issues.push({ field: 'startsAt', code: 'EVENT_START_REQUIRED', message: 'La fecha de inicio es obligatoria para publicar' });
  }
  if (ends == null) {
    issues.push({ field: 'endsAt', code: 'EVENT_END_REQUIRED', message: 'La fecha de término es obligatoria para publicar' });
  }
  if (trimToNull(body.timezone) == null) {
    issues.push({ field: 'timezone', code: 'EVENT_TIMEZONE_REQUIRED', message: 'La zona horaria es obligatoria para publicar' });
  }
  if (opens == null) {
    issues.push({ field: 'registrationOpensAt', code: 'EVENT_REGISTRATION_OPEN_REQUIRED', message: 'La apertura de inscripciones es obligatoria para publicar' });
  }
  if (closes == null) {
    issues.push({ field: 'registrationClosesAt', code: 'EVENT_REGISTRATION_CLOSE_REQUIRED', message: 'El cierre de inscripciones es obligatorio para publicar' });
  }
  const place = trimToNull(body.locationName);
  const campus = trimToNull(body.campusName);
  const access = trimToNull(body.virtualAccess);
  const hasPlace = place != null || campus != null;
  if (modality === 'PRESENTIAL' && !hasPlace) {
    issues.push({ field: 'locationName', code: 'EVENT_PLACE_REQUIRED', message: 'Indique la sede o el lugar para un evento presencial' });
  }
  if (modality === 'VIRTUAL' && access == null) {
    issues.push({ field: 'virtualAccess', code: 'EVENT_VIRTUAL_ACCESS_REQUIRED', message: 'Indique el enlace o las instrucciones de acceso virtual' });
  }
  if (modality === 'HYBRID') {
    if (!hasPlace) {
      issues.push({ field: 'locationName', code: 'EVENT_PLACE_REQUIRED', message: 'Indique la sede o el lugar para un evento híbrido' });
    }
    if (access == null) {
      issues.push({ field: 'virtualAccess', code: 'EVENT_VIRTUAL_ACCESS_REQUIRED', message: 'Indique el enlace o las instrucciones de acceso virtual' });
    }
  }
  return issues;
}

function windowIssues(
  starts: Date | null,
  ends: Date | null,
  opens: Date | null,
  closes: Date | null,
  allowDuringEvent: boolean
): EventFieldIssue[] {
  const issues: EventFieldIssue[] = [];
  if (starts != null && ends != null && ends.getTime() < starts.getTime()) {
    issues.push({ field: 'endsAt', code: 'EVENT_ENDS_BEFORE_START', message: 'El término no puede ser anterior al inicio' });
  }
  if (opens != null && closes != null && closes.getTime() < opens.getTime()) {
    issues.push({
      field: 'registrationClosesAt',
      code: 'EVENT_REGISTRATION_WINDOW_INVALID',
      message: 'El cierre de inscripciones no puede ser anterior a la apertura'
    });
  }
  if (!allowDuringEvent && starts != null && closes != null && closes.getTime() > starts.getTime()) {
    issues.push({
      field: 'registrationClosesAt',
      code: 'EVENT_REGISTRATION_AFTER_START',
      message: 'El cierre de inscripciones debe ser anterior o igual al inicio, salvo que permita inscribir durante la actividad'
    });
  }
  return issues;
}

function unitIssue(body: EventFormValues, publish: boolean): EventFieldIssue | null {
  const code = trimToNull(body.organizingUnitCode);
  const text = trimToNull(body.organizingUnit);
  if (!publish) {
    return null;
  }
  if (code == null && text == null) {
    return { field: 'organizingUnitCode', code: 'EVENT_UNIT_REQUIRED', message: 'La unidad organizadora es obligatoria para publicar' };
  }
  if ((code == null || code === EVENT_UNIT_OTHER) && text == null) {
    return { field: 'organizingUnit', code: 'EVENT_UNIT_OTHER_REQUIRED', message: 'Indique el nombre de la unidad organizadora' };
  }
  return null;
}

function titleIssue(raw?: string | null): EventFieldIssue | null {
  const title = trimToNull(raw);
  if (title == null) {
    return { field: 'title', code: 'EVENT_TITLE_REQUIRED', message: 'El nombre del evento es obligatorio' };
  }
  if (title.length > EVENT_TITLE_MAX) {
    return { field: 'title', code: 'EVENT_TITLE_TOO_LONG', message: 'El nombre no puede superar 255 caracteres' };
  }
  return null;
}

function capacityIssue(raw?: string | null): EventFieldIssue | null {
  const value = trimToNull(raw);
  if (value == null) {
    return null;
  }
  if (!/^\d+$/.test(value)) {
    return { field: 'capacity', code: 'EVENT_CAPACITY_INVALID', message: 'El cupo debe ser un entero positivo, o vacío si es ilimitado' };
  }
  const capacity = Number.parseInt(value, 10);
  if (capacity < 1 || capacity > 100000) {
    return { field: 'capacity', code: 'EVENT_CAPACITY_INVALID', message: 'El cupo debe ser un entero positivo, o vacío si es ilimitado' };
  }
  return null;
}

function blankRowsIssue(raw?: string | null): EventFieldIssue | null {
  if (raw == null || String(raw).trim() === '') {
    return null;
  }
  const text = String(raw).trim();
  if (!/^-?\d+$/.test(text)) {
    return { field: 'blankSignatureRows', code: 'EVENT_BLANK_ROWS_INVALID', message: 'Las filas en blanco del acta deben estar entre 0 y 200' };
  }
  const value = Number.parseInt(text, 10);
  if (value < 0 || value > 200) {
    return { field: 'blankSignatureRows', code: 'EVENT_BLANK_ROWS_INVALID', message: 'Las filas en blanco del acta deben estar entre 0 y 200' };
  }
  return null;
}

function lengthIssue(field: string, raw: string | null | undefined, max: number, code: string): EventFieldIssue | null {
  const value = trimToNull(raw);
  if (value != null && value.length > max) {
    return { field, code, message: 'El texto supera el máximo permitido' };
  }
  return null;
}

function parseType(raw?: string | null): string | null {
  const value = trimToNull(raw)?.toUpperCase().replace(/-/g, '_');
  return value && EVENT_TYPES.has(value) ? value : null;
}

function parseModality(raw?: string | null): string | null {
  const value = trimToNull(raw)?.toUpperCase();
  return value && EVENT_MODALITIES.has(value) ? value : null;
}

function parseDate(raw?: string | null): Date | null {
  if (raw == null || raw.trim() === '') {
    return null;
  }
  let value = raw.trim();
  if (value.endsWith('Z')) {
    value = value.slice(0, -1);
  }
  const plus = value.indexOf('+', 10);
  const tzSep = value.indexOf('-', 11);
  if (plus > 0) {
    value = value.slice(0, plus);
  } else if (tzSep > 0 && value.charAt(tzSep - 1) !== 'T') {
    value = value.slice(0, tzSep);
  }
  if (value.length > 19) {
    value = value.slice(0, 19);
  }
  if (value.length !== 16 && value.length !== 19) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function add(issues: EventFieldIssue[], issue: EventFieldIssue | null): void {
  if (issue) {
    issues.push(issue);
  }
}
