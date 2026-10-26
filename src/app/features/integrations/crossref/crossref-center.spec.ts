import { canShowCrossrefSync, crossrefEmptyKind } from './crossref-center.component';

describe('Crossref center helpers', () => {
  it('hides sync without ADMINISTRAR or when disabled', () => {
    expect(canShowCrossrefSync(false, true, true)).toBe(false);
    expect(canShowCrossrefSync(true, false, true)).toBe(false);
    expect(canShowCrossrefSync(true, true, false)).toBe(false);
    expect(canShowCrossrefSync(true, true, true)).toBe(true);
  });

  it('distinguishes empty states', () => {
    expect(
      crossrefEmptyKind({
        loadError: false,
        enabled: false,
        configured: false,
        tablesAvailable: false,
        lastStatus: null,
        evaluated: 0,
        itemCount: 0,
        filtered: false
      })
    ).toBe('disabled');
    expect(
      crossrefEmptyKind({
        loadError: false,
        enabled: true,
        configured: true,
        tablesAvailable: true,
        lastStatus: null,
        evaluated: 0,
        itemCount: 0,
        filtered: false
      })
    ).toBe('never');
    expect(
      crossrefEmptyKind({
        loadError: false,
        enabled: true,
        configured: true,
        tablesAvailable: true,
        lastStatus: 'COMPLETED',
        evaluated: 10,
        itemCount: 0,
        filtered: true
      })
    ).toBe('filtered');
    expect(
      crossrefEmptyKind({
        loadError: false,
        enabled: true,
        configured: true,
        tablesAvailable: true,
        lastStatus: 'COMPLETED',
        evaluated: 0,
        itemCount: 0,
        filtered: false
      })
    ).toBe('none');
    expect(
      crossrefEmptyKind({
        loadError: true,
        enabled: true,
        configured: true,
        tablesAvailable: true,
        lastStatus: null,
        evaluated: 0,
        itemCount: 0,
        filtered: false
      })
    ).toBeNull();
  });
});
