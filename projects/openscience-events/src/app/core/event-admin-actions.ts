export type EventAdminStatus = 'DRAFT' | 'PUBLISHED' | 'FINISHED' | 'CANCELLED' | string;

export interface EventAdminActionFlags {
  canPublish: boolean;
  canUnpublish: boolean;
  canFinish: boolean;
  canCancel: boolean;
  canEdit: boolean;
}

export function eventAdminActionFlags(status?: string | null): EventAdminActionFlags {
  const value = (status ?? '').toUpperCase();
  const published = value === 'PUBLISHED';
  return {
    canPublish: value === 'DRAFT',
    canUnpublish: published,
    canFinish: published,
    canCancel: published,
    canEdit: value !== 'FINISHED' && value !== 'CANCELLED'
  };
}

export function eventStatusClass(status?: string | null): string {
  const value = (status ?? '').toLowerCase();
  if (value === 'published') {
    return 'is-published';
  }
  if (value === 'finished') {
    return 'is-finished';
  }
  if (value === 'cancelled') {
    return 'is-cancelled';
  }
  return 'is-draft';
}
