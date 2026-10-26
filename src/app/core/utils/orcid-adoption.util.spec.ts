import {
  latestCompleteOrcidMonth,
  orcidAdoptionLinePoints,
  orcidFirstSeenIsPureAddDate,
  orcidFirstSeenLabelKey
} from './orcid-adoption.util';

describe('ORCID adoption presentation', () => {
  it('labels observed vs baseline first-seen without calling it a PURE add date', () => {
    expect(orcidFirstSeenLabelKey('OBSERVED')).toBe('people.orcidObservedSince');
    expect(orcidFirstSeenLabelKey('BASELINE')).toBe('people.orcidPresentAtTrackingStart');
    expect(orcidFirstSeenIsPureAddDate()).toBe(false);
  });

  it('builds a monthly polyline from complete percentages only', () => {
    const points = orcidAdoptionLinePoints([
      { month: '2026-09', totalPersons: null, personsWithOrcid: null, percentage: null, complete: false },
      { month: '2026-10', totalPersons: 1800, personsWithOrcid: 1100, percentage: 61.1, complete: true },
      { month: '2026-11', totalPersons: 1810, personsWithOrcid: 1150, percentage: 63.5, complete: true }
    ]);
    expect(points).toContain(',');
    expect(points.split(' ').length).toBe(2);
  });

  it('returns the latest complete month for the identity card', () => {
    const latest = latestCompleteOrcidMonth([
      { month: '2026-10', totalPersons: 1800, personsWithOrcid: 1100, percentage: 61.1, complete: true },
      { month: '2026-11', totalPersons: 1810, personsWithOrcid: 1150, percentage: 63.5, complete: true },
      { month: '2026-12', totalPersons: null, personsWithOrcid: null, percentage: null, complete: false }
    ]);
    expect(latest?.month).toBe('2026-11');
    expect(latest?.percentage).toBe(63.5);
  });
});
