import { AdminUserSummary } from '../../../core/models/openscience.models';

export function isDeletedUser(row: Pick<AdminUserSummary, 'status' | 'deletedAt'>): boolean {
  return row.status === 'DELETED' || row.deletedAt != null;
}

export function canOfferUserDelete(
  row: Pick<AdminUserSummary, 'id' | 'status' | 'deletedAt'>,
  options: { currentUserId: string | null | undefined; canAdminister: boolean }
): boolean {
  if (!options.canAdminister || isDeletedUser(row)) {
    return false;
  }
  if (options.currentUserId && String(row.id) === String(options.currentUserId)) {
    return false;
  }
  return true;
}

export function canOfferUserMutations(row: Pick<AdminUserSummary, 'status' | 'deletedAt'>): boolean {
  return !isDeletedUser(row);
}
