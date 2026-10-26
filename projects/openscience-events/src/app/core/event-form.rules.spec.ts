import { collectEventIssues, EVENT_DEFAULT_BLANK_ROWS, EVENT_DEFAULT_TIMEZONE } from './event-form.rules.ts';

function validPublish() {
  return {
    title: 'Seminario de ciencia abierta',
    description: 'Programa y objetivos del evento.',
    eventType: 'SEMINAR',
    modality: 'PRESENTIAL',
    startsAt: '2026-03-01T10:00:00',
    endsAt: '2026-03-01T13:00:00',
    timezone: EVENT_DEFAULT_TIMEZONE,
    organizingUnitCode: 'CIENCIA_ABIERTA',
    locationName: 'Aula Magna',
    campusName: 'Sede Santiago',
    registrationOpensAt: '2026-02-01T10:00:00',
    registrationClosesAt: '2026-02-28T18:00:00',
    blankSignatureRows: 10
  };
}

function hasCode(issues: Array<{ code: string }>, code: string): boolean {
  return issues.some((issue) => issue.code === code);
}

function assert(condition: unknown, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

export function runEventFormRulesTests(): void {
  assert(collectEventIssues({ title: '  Jornada de ciencia abierta  ' }, false).length === 0, 'draft only name');
  assert(collectEventIssues({ title: '   ' }, false)[0]?.code === 'EVENT_TITLE_REQUIRED', 'whitespace title');

  const emptyPublish = collectEventIssues({ title: 'Seminario' }, true);
  for (const code of [
    'EVENT_DESCRIPTION_REQUIRED',
    'EVENT_TYPE_REQUIRED',
    'EVENT_MODALITY_REQUIRED',
    'EVENT_START_REQUIRED',
    'EVENT_END_REQUIRED',
    'EVENT_TIMEZONE_REQUIRED',
    'EVENT_UNIT_REQUIRED',
    'EVENT_REGISTRATION_OPEN_REQUIRED',
    'EVENT_REGISTRATION_CLOSE_REQUIRED'
  ]) {
    assert(hasCode(emptyPublish, code), code);
  }

  assert(collectEventIssues(validPublish(), true).length === 0, 'publish valid');

  const endsBefore = collectEventIssues({ ...validPublish(), endsAt: '2026-03-01T09:00:00' }, true);
  assert(hasCode(endsBefore, 'EVENT_ENDS_BEFORE_START'), 'ends before start');

  const windowInvalid = collectEventIssues({ ...validPublish(), registrationClosesAt: '2026-02-01T09:00:00' }, true);
  assert(hasCode(windowInvalid, 'EVENT_REGISTRATION_WINDOW_INVALID'), 'registration window');

  const afterStart = collectEventIssues({ ...validPublish(), registrationClosesAt: '2026-03-01T12:00:00' }, true);
  assert(hasCode(afterStart, 'EVENT_REGISTRATION_AFTER_START'), 'close after start');

  const during = collectEventIssues(
    { ...validPublish(), registrationClosesAt: '2026-03-01T12:00:00', allowRegistrationDuringEvent: true },
    true
  );
  assert(during.length === 0, 'allow during event');

  assert(
    hasCode(collectEventIssues({ ...validPublish(), locationName: null, campusName: null }, true), 'EVENT_PLACE_REQUIRED'),
    'presential place'
  );
  assert(
    hasCode(
      collectEventIssues({ ...validPublish(), modality: 'VIRTUAL', locationName: null, campusName: null, virtualAccess: null }, true),
      'EVENT_VIRTUAL_ACCESS_REQUIRED'
    ),
    'virtual access'
  );
  assert(
    hasCode(collectEventIssues({ ...validPublish(), modality: 'HYBRID', virtualAccess: null }, true), 'EVENT_VIRTUAL_ACCESS_REQUIRED'),
    'hybrid access'
  );
  assert(
    collectEventIssues(
      { ...validPublish(), modality: 'HYBRID', virtualAccess: 'https://meet.uss.cl/evento', locationName: null, campusName: 'Sede Santiago' },
      true
    ).length === 0,
    'hybrid valid'
  );

  assert(hasCode(collectEventIssues({ ...validPublish(), capacity: 0 }, false), 'EVENT_CAPACITY_INVALID'), 'capacity 0');
  assert(collectEventIssues({ ...validPublish(), capacity: 25, blankSignatureRows: 0 }, false).length === 0, 'blank rows 0');
  assert(collectEventIssues({ ...validPublish(), blankSignatureRows: 10 }, false).length === 0, 'blank rows 10');
  assert(hasCode(collectEventIssues({ ...validPublish(), blankSignatureRows: -1 }, false), 'EVENT_BLANK_ROWS_INVALID'), 'blank rows -1');
  assert(EVENT_DEFAULT_BLANK_ROWS === 10, 'default blank rows');
  assert(
    hasCode(collectEventIssues({ ...validPublish(), organizingUnitCode: 'OTHER', organizingUnit: null }, true), 'EVENT_UNIT_OTHER_REQUIRED'),
    'other empty'
  );
  assert(collectEventIssues({ ...validPublish(), organizingUnitCode: 'OTHER', organizingUnit: 'Facultad de Medicina' }, true).length === 0, 'other named');
  assert(collectEventIssues({ ...validPublish(), organizingUnitCode: 'DISEI', organizingUnit: 'texto viejo' }, true).length === 0, 'official ignores other text');
}

runEventFormRulesTests();
