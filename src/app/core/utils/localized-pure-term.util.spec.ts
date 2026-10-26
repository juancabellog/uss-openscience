import { localizedPureTerm, workflowStepCode } from './localized-pure-term.util';

describe('localizedPureTerm', () => {
  it('prefers Spanish, then English, then the code, then empty', () => {
    expect(localizedPureTerm('es', 'Para su aprobación', 'For approval', 'forApproval')).toBe('Para su aprobación');
    expect(localizedPureTerm('es', null, 'For approval', 'forApproval')).toBe('For approval');
    expect(localizedPureTerm('es', null, null, 'forApproval')).toBe('forApproval');
    expect(localizedPureTerm('es', null, null, null)).toBe('');
  });

  it('prefers English, then Spanish, then the code, then empty', () => {
    expect(localizedPureTerm('en', 'Público - Sin restricción', 'Public - No restriction', 'FREE')).toBe(
      'Public - No restriction'
    );
    expect(localizedPureTerm('en', 'Público - Sin restricción', null, 'FREE')).toBe('Público - Sin restricción');
    expect(localizedPureTerm('en', null, null, 'FREE')).toBe('FREE');
    expect(localizedPureTerm('en', null, null, null)).toBe('');
  });
});

describe('workflowStepCode', () => {
  it('shows the step only when a localized label is already the primary text', () => {
    expect(workflowStepCode('Para su aprobación', 'forApproval')).toBe('forApproval');
    expect(workflowStepCode('forApproval', 'forApproval')).toBe('');
    expect(workflowStepCode('', 'forApproval')).toBe('');
    expect(workflowStepCode('Para su aprobación', null)).toBe('');
  });
});
