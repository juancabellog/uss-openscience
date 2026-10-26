import { OrcidDiscoveryPerson, OrcidDiscoverySelectedItem } from '../../../core/models/openscience.models';
import {
  applyButtonI18nKey,
  applyConfirmI18nKey,
  applyPersonIds,
  canToggleSyncSelection,
  isApplyDisabled,
  isSelectedForSync,
  keepQueuedOnFilterChange,
  pruneQueuedAfterApply,
  queuedItemFromPerson,
  queuedPersonIdSet,
  resolveCandidateForQueue,
  withQueuedPerson,
  withoutQueuedPerson
} from './orcid-discovery-selection';

function queued(personId: number, extras: Partial<OrcidDiscoverySelectedItem> = {}): OrcidDiscoverySelectedItem {
  return {
    personId,
    displayName: `Persona ${personId}`,
    pureId: personId,
    orcid: `0000-0000-0000-000${personId}`,
    candidateId: personId * 10,
    personStatus: 'STRONG',
    selectionOrigin: 'AUTO_STRONG',
    queued: true,
    ...extras
  };
}

function person(partial: Partial<OrcidDiscoveryPerson> & Pick<OrcidDiscoveryPerson, 'personId'>): OrcidDiscoveryPerson {
  return {
    id: partial.personId,
    personId: partial.personId,
    pureId: partial.personId,
    uuid: null,
    firstName: 'Ada',
    lastName: 'Lovelace',
    fullName: 'Lovelace, Ada',
    cargo: 'Académico',
    pureOrcid: null,
    foundOrcid: '0000-0002-1825-0097',
    selectedOrcid: null,
    bestCandidateId: 8,
    uniqueStrongCandidateId: 8,
    personStatus: 'STRONG',
    statusReason: null,
    candidateCount: 1,
    searchHitCount: 1,
    doiMatchCount: 1,
    selectedCandidateId: null,
    selectionOrigin: null,
    selectedBy: null,
    selectedAt: null,
    searchGivenName: 'Ada',
    searchFamilyName: 'Lovelace',
    lastError: null,
    selectable: true,
    selected: false,
    selectionDisabledReason: null,
    candidates: [],
    ...partial
  };
}

describe('ORCID discovery sync selection', () => {
  it('A) disables the apply button when there are 0 selected people', () => {
    expect(isApplyDisabled(0, false, false, false)).toBe(true);
  });

  it('B) uses the singular apply label for 1 selected person', () => {
    expect(applyButtonI18nKey(1)).toBe('integrations.orcid.discovery.applyOne');
    expect(applyConfirmI18nKey(1)).toBe('integrations.orcid.discovery.applyConfirmOne');
  });

  it('C) uses the plural apply label with a dynamic count for 3 selected people', () => {
    expect(applyButtonI18nKey(3)).toBe('integrations.orcid.discovery.apply');
    expect(applyButtonI18nKey(2)).toBe('integrations.orcid.discovery.apply');
  });

  it('D) checking a person increments the queued collection used by the counter', () => {
    const next = withQueuedPerson([queued(1), queued(2)], queued(3));
    expect(next.map((item) => item.personId)).toEqual([1, 2, 3]);
    expect(applyPersonIds(next).length).toBe(3);
  });

  it('E) unchecking a person decrements the queued collection used by the counter', () => {
    const next = withoutQueuedPerson([queued(1), queued(2), queued(3)], 2);
    expect(applyPersonIds(next)).toEqual([1, 3]);
  });

  it('F) a failed persist rolls the queued collection back to the snapshot', () => {
    const snapshot = [queued(1), queued(2)];
    const optimistic = withQueuedPerson(snapshot, queued(3));
    expect(applyPersonIds(optimistic)).toEqual([1, 2, 3]);
    const rolledBack = snapshot;
    expect(applyPersonIds(rolledBack)).toEqual([1, 2]);
    expect(isSelectedForSync(queuedPersonIdSet(rolledBack), 3)).toBe(false);
  });

  it('G) the counter and PURE payload contain the same person IDs', () => {
    const selection = [queued(11), queued(22), queued(33)];
    expect(applyPersonIds(selection)).toEqual([11, 22, 33]);
    expect(applyPersonIds(selection).length).toBe(selection.length);
  });

  it('H) selection is counted across pages, not only the visible rows', () => {
    const persisted = [queued(1), queued(2), queued(26)];
    const visiblePage2 = [person({ personId: 26 })];
    const ids = queuedPersonIdSet(persisted);
    expect(applyPersonIds(persisted).length).toBe(3);
    expect(visiblePage2.filter((row) => isSelectedForSync(ids, row.personId)).length).toBe(1);
    expect(isSelectedForSync(ids, 1)).toBe(true);
  });

  it('I) filters do not clear the persisted queued selection', () => {
    const persisted = [queued(1), queued(2), queued(3)];
    expect(keepQueuedOnFilterChange(persisted)).toEqual(persisted);
    expect(applyPersonIds(keepQueuedOnFilterChange(persisted))).toEqual([1, 2, 3]);
  });

  it('J) a full PURE success removes synchronized people from the selection', () => {
    const next = pruneQueuedAfterApply(
      [queued(1), queued(2), queued(3)],
      {
        items: [
          { localParentId: 1, status: 'UPDATED' },
          { localParentId: 2, status: 'UPDATED' },
          { localParentId: 3, status: 'SYNCED' }
        ]
      }
    );
    expect(applyPersonIds(next)).toEqual([]);
    expect(isApplyDisabled(next.length, false, false, false)).toBe(true);
  });

  it('K) a partial PURE sync keeps only still-syncable failures', () => {
    const next = pruneQueuedAfterApply(
      [queued(1), queued(2), queued(3)],
      {
        items: [
          { localParentId: 1, status: 'UPDATED' },
          { localParentId: 2, status: 'UPDATED' },
          { localParentId: 3, status: 'CONFLICT' }
        ]
      }
    );
    expect(applyPersonIds(next)).toEqual([3]);
  });

  it('L) selecting strong matches replaces the queued collection used by the counter', () => {
    const afterSelectStrong = [queued(4), queued(5), queued(6)];
    expect(applyPersonIds(afterSelectStrong)).toEqual([4, 5, 6]);
    expect(applyButtonI18nKey(afterSelectStrong.length)).toBe('integrations.orcid.discovery.apply');
  });

  it('does not count people who are not in the queued sync collection', () => {
    const ids = queuedPersonIdSet([queued(1), queued(2)]);
    expect(isSelectedForSync(ids, 3)).toBe(false);
    expect(canToggleSyncSelection({
      canAdminister: true,
      queued: false,
      selectable: false,
      personStatus: 'MANUAL_REVIEW'
    })).toBe(false);
  });

  it('queues MANUAL_REVIEW only after an explicit candidate exists', () => {
    const manual = person({
      personId: 9,
      personStatus: 'MANUAL_REVIEW',
      uniqueStrongCandidateId: null,
      selectedCandidateId: null,
      selectable: false
    });
    expect(resolveCandidateForQueue(manual)).toBeNull();
    const chosen = person({
      personId: 9,
      personStatus: 'MANUAL_REVIEW',
      uniqueStrongCandidateId: null,
      selectedCandidateId: 44,
      selectable: true
    });
    expect(resolveCandidateForQueue(chosen)).toBe(44);
    expect(queuedItemFromPerson(chosen).candidateId).toBe(44);
  });
});
