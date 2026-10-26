import {
  canOfferSyncCancel,
  canStartSync,
  conservativeSyncPolicy,
  isActiveSyncStatus,
  isSyncControlDisabled,
  mapSyncModePolicy,
  menuSyncModes,
  metricsMenuHelpKey,
  metricsMenuTitleKey,
  openAlexFullConfirmBodyKey,
  primarySyncActionKey,
  primarySyncMode,
  shouldConfirmSyncMode,
  showsSyncModeMenu,
  supportedSyncModes,
  supportsIncrementalSync,
  syncModeLabelKey,
  type SyncModePolicy
} from './sync-mode.util';

function policy(partial: Partial<SyncModePolicy>): SyncModePolicy {
  return {
    entity: 'ORCID',
    supportedSyncModes: ['FULL', 'INCREMENTAL'],
    availableSyncModes: ['FULL', 'INCREMENTAL'],
    hasValidBaseline: false,
    recommendedSyncMode: 'FULL',
    ...partial
  };
}

describe('sync-mode.util', () => {
  it('keeps FULL+INCREMENTAL as a capability for ORCID, Unpaywall, Crossref, OpenAlex and DSpace', () => {
    for (const entity of ['ORCID', 'UNPAYWALL', 'CROSSREF', 'OPENALEX', 'DSPACE'] as const) {
      expect(supportsIncrementalSync(entity)).toBe(true);
      expect(supportedSyncModes(entity)).toEqual(['FULL', 'INCREMENTAL']);
      expect(conservativeSyncPolicy(entity).recommendedSyncMode).toBe('FULL');
      expect(conservativeSyncPolicy(entity).availableSyncModes).toEqual(['FULL']);
      expect(showsSyncModeMenu(conservativeSyncPolicy(entity))).toBe(false);
    }
  });

  it('keeps FULL-only integrations on a simple Sync action without dropdown', () => {
    for (const entity of ['PEOPLE', 'RESEARCH_OUTPUT', 'JOURNALS', 'PROJECTS', 'ORGANIZATIONS'] as const) {
      const item = conservativeSyncPolicy(entity);
      expect(supportsIncrementalSync(entity)).toBe(false);
      expect(supportedSyncModes(entity)).toEqual(['FULL']);
      expect(primarySyncActionKey(item, false)).toBe('sync.action');
      expect(showsSyncModeMenu(item)).toBe(false);
    }
  });

  it('never-synced card recommends full and does not offer incremental', () => {
    const neverSynced = policy({
      hasValidBaseline: false,
      recommendedSyncMode: 'FULL',
      availableSyncModes: ['FULL']
    });
    expect(primarySyncActionKey(neverSynced, false)).toBe('sync.actionFull');
    expect(primarySyncMode(neverSynced)).toBe('FULL');
    expect(showsSyncModeMenu(neverSynced)).toBe(false);
    expect(menuSyncModes(neverSynced)).toEqual([]);
    expect(shouldConfirmSyncMode('FULL', neverSynced)).toBe(true);
    expect(shouldConfirmSyncMode('INCREMENTAL', neverSynced)).toBe(true);
  });

  it('valid baseline recommends incremental and keeps FULL in the dropdown', () => {
    const ready = policy({
      hasValidBaseline: true,
      recommendedSyncMode: 'INCREMENTAL',
      availableSyncModes: ['FULL', 'INCREMENTAL']
    });
    expect(primarySyncActionKey(ready, false)).toBe('sync.actionIncremental');
    expect(showsSyncModeMenu(ready)).toBe(true);
    expect(menuSyncModes(ready)).toEqual(['FULL']);
    expect(shouldConfirmSyncMode('FULL', ready)).toBe(true);
    expect(shouldConfirmSyncMode('INCREMENTAL', ready)).toBe(true);
  });

  it('failed incremental still recommends incremental', () => {
    const failedIncremental = policy({
      hasValidBaseline: true,
      recommendedSyncMode: 'INCREMENTAL',
      availableSyncModes: ['FULL', 'INCREMENTAL'],
      lastExecution: {
        syncId: '12',
        entity: 'ORCID',
        status: 'FAILED',
        syncMode: 'INCREMENTAL',
        processed: 98,
        created: 0,
        updated: 0,
        unchanged: 0,
        reactivated: 0,
        markedInactive: 0,
        errors: 2
      }
    });
    expect(primarySyncMode(failedIncremental)).toBe('INCREMENTAL');
    expect(showsSyncModeMenu(failedIncremental)).toBe(true);
  });

  it('failed full recommends full while incremental remains available', () => {
    const failedFull = policy({
      hasValidBaseline: true,
      recommendedSyncMode: 'FULL',
      availableSyncModes: ['FULL', 'INCREMENTAL']
    });
    expect(primarySyncActionKey(failedFull, false)).toBe('sync.actionFull');
    expect(showsSyncModeMenu(failedFull)).toBe(true);
    expect(menuSyncModes(failedFull)).toEqual(['INCREMENTAL']);
  });

  it('disables the control while a sync is running', () => {
    const ready = policy({
      hasValidBaseline: true,
      recommendedSyncMode: 'INCREMENTAL',
      availableSyncModes: ['FULL', 'INCREMENTAL']
    });
    expect(primarySyncActionKey(ready, true)).toBe('sync.runningAction');
    expect(isSyncControlDisabled(true, true)).toBe(true);
    expect(isSyncControlDisabled(false, true)).toBe(false);
    expect(isSyncControlDisabled(false, true, true)).toBe(true);
  });

  it('always confirms FULL and INCREMENTAL before calling the backend', () => {
    const ready = policy({
      hasValidBaseline: true,
      recommendedSyncMode: 'INCREMENTAL',
      availableSyncModes: ['FULL', 'INCREMENTAL']
    });
    expect(shouldConfirmSyncMode('FULL', ready)).toBe(true);
    expect(shouldConfirmSyncMode('INCREMENTAL', ready)).toBe(true);
  });

  it('uses the backend work count for an OpenAlex FULL warning and a shorter incremental copy', () => {
    expect(openAlexFullConfirmBodyKey(22515)).toBe('sync.confirmOpenAlexFullBody');
    expect(openAlexFullConfirmBodyKey(null)).toBe('sync.confirmOpenAlexFullBodyUnknown');
    expect(openAlexFullConfirmBodyKey(0)).toBe('sync.confirmOpenAlexFullBodyUnknown');
  });

  it('offers cancel only for an administrative RUNNING sync, never as the Synchronizing button', () => {
    expect(canOfferSyncCancel(true, 'RUNNING')).toBe(true);
    expect(canOfferSyncCancel(true, 'CANCEL_REQUESTED')).toBe(false);
    expect(canOfferSyncCancel(false, 'RUNNING')).toBe(false);
    expect(isActiveSyncStatus('CANCEL_REQUESTED')).toBe(true);
    expect(isActiveSyncStatus('CANCELLED')).toBe(false);
    expect(isActiveSyncStatus('FAILED')).toBe(false);
    expect(primarySyncActionKey(policy({}), true)).toBe('sync.runningAction');
  });

  it('lets ADMINISTRAR start a sync and hides the action for CONSULTAR', () => {
    expect(canStartSync(true, true)).toBe(true);
    expect(canStartSync(false, true)).toBe(false);
    expect(canStartSync(true, false)).toBe(false);
    expect(isSyncControlDisabled(false, canStartSync(false, true))).toBe(true);
  });

  it('hides the mode menu when availableSyncModes omits incremental', () => {
    const gated = policy({
      hasValidBaseline: false,
      recommendedSyncMode: 'FULL',
      availableSyncModes: ['FULL']
    });
    expect(showsSyncModeMenu(gated)).toBe(false);
    expect(menuSyncModes(gated)).toEqual([]);
    expect(primarySyncMode(gated)).toBe('FULL');
  });

  it('maps a dual-mode policy without a baseline as full-only', () => {
    const mapped = mapSyncModePolicy(
      {
        entity: 'UNPAYWALL',
        supportedSyncModes: ['FULL', 'INCREMENTAL'],
        availableSyncModes: ['FULL'],
        hasValidBaseline: false,
        recommendedSyncMode: 'FULL'
      },
      'UNPAYWALL'
    );
    expect(mapped.hasValidBaseline).toBe(false);
    expect(mapped.availableSyncModes).toEqual(['FULL']);
    expect(primarySyncActionKey(mapped, false)).toBe('sync.actionFull');
    expect(showsSyncModeMenu(mapped)).toBe(false);
  });

  it('renders incremental as the primary action for dual-mode integration summaries', () => {
    for (const entity of ['ORCID', 'UNPAYWALL', 'CROSSREF', 'DSPACE'] as const) {
      const mapped = mapSyncModePolicy(
        {
          entity,
          supportedSyncModes: ['FULL', 'INCREMENTAL'],
          availableSyncModes: ['FULL', 'INCREMENTAL'],
          hasValidBaseline: true,
          recommendedSyncMode: 'INCREMENTAL'
        },
        entity
      );
      expect(primarySyncActionKey(mapped, false)).toBe('sync.actionIncremental');
      expect(showsSyncModeMenu(mapped)).toBe(true);
      expect(menuSyncModes(mapped)).toEqual(['FULL']);
    }
  });

  it('treats metrics entities as dual-mode and confirms FULL only', () => {
    for (const entity of ['PERSON_METRICS', 'RESEARCH_OUTPUT_METRICS', 'JOURNAL_METRICS'] as const) {
      expect(supportsIncrementalSync(entity)).toBe(true);
      expect(supportedSyncModes(entity)).toEqual(['FULL', 'INCREMENTAL']);
      const neverSynced = conservativeSyncPolicy(entity);
      expect(primarySyncActionKey(neverSynced, false)).toBe('sync.metrics.action');
      expect(shouldConfirmSyncMode('FULL', neverSynced)).toBe(true);
      expect(shouldConfirmSyncMode('INCREMENTAL', neverSynced)).toBe(false);
      const ready = mapSyncModePolicy(
        {
          entity,
          supportedSyncModes: ['FULL', 'INCREMENTAL'],
          availableSyncModes: ['FULL', 'INCREMENTAL'],
          hasValidBaseline: true,
          recommendedSyncMode: 'INCREMENTAL'
        },
        entity
      );
      expect(primarySyncActionKey(ready, false)).toBe('sync.metrics.actionIncremental');
      expect(primarySyncActionKey(ready, true)).toBe('sync.metrics.runningAction');
      expect(showsSyncModeMenu(ready)).toBe(true);
      expect(menuSyncModes(ready)).toEqual(['FULL']);
      expect(shouldConfirmSyncMode('INCREMENTAL', ready)).toBe(false);
      expect(shouldConfirmSyncMode('FULL', ready)).toBe(true);
    }
    expect(metricsMenuTitleKey('INCREMENTAL')).toBe('sync.metrics.menuIncremental');
    expect(metricsMenuTitleKey('FULL')).toBe('sync.metrics.menuFull');
    expect(metricsMenuHelpKey('INCREMENTAL')).toBe('sync.metrics.incrementalHelp');
    expect(metricsMenuHelpKey('FULL')).toBe('sync.metrics.fullHelp');
  });

  it('exposes UI labels and tolerates a null historical mode', () => {
    expect(syncModeLabelKey('INCREMENTAL')).toBe('sync.mode.incremental');
    expect(syncModeLabelKey('FULL')).toBe('sync.mode.full');
    expect(syncModeLabelKey('RECOVERY')).toBe('sync.mode.recovery');
    expect(syncModeLabelKey(null)).toBeNull();
    expect(syncModeLabelKey(undefined)).toBeNull();
  });
});
