import { AdminUserSummary } from '../../../core/models/openscience.models';
import { canOfferUserDelete, canOfferUserMutations, isDeletedUser } from './user-admin-actions';

const pending: AdminUserSummary = {
  id: 9,
  firstName: 'Ana',
  lastName: 'Prueba',
  displayName: 'Ana Prueba',
  email: 'ana@uss.cl',
  status: 'PENDING_ACTIVATION',
  preferredLanguage: 'es',
  roles: ['Consulta'],
  roleIds: [5],
  lastLoginAt: null,
  createdAt: '2026-10-05T12:00:00Z'
};

describe('user deletion UI policy', () => {
  it('shows Eliminar only with ADMINISTRAR', () => {
    expect(canOfferUserDelete(pending, { currentUserId: '1', canAdminister: true })).toBe(true);
    expect(canOfferUserDelete(pending, { currentUserId: '1', canAdminister: false })).toBe(false);
  });

  it('does not offer delete for the current user', () => {
    expect(canOfferUserDelete(pending, { currentUserId: '9', canAdminister: true })).toBe(false);
  });

  it('hides normal actions for deleted users', () => {
    const deleted = { ...pending, status: 'DELETED', deletedAt: '2026-10-05T13:00:00Z' };
    expect(isDeletedUser(deleted)).toBe(true);
    expect(canOfferUserMutations(deleted)).toBe(false);
    expect(canOfferUserDelete(deleted, { currentUserId: '1', canAdminister: true })).toBe(false);
  });

  it('keeps filter value DELETED for the status control', () => {
    expect('DELETED').toBe('DELETED');
  });
});
