import { eventAdminActionFlags, eventStatusClass } from './event-admin-actions.ts';

function assert(condition: unknown, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

assert(eventAdminActionFlags('DRAFT').canPublish, 'draft can publish');
assert(!eventAdminActionFlags('DRAFT').canUnpublish, 'draft cannot unpublish');
assert(eventAdminActionFlags('DRAFT').canEdit, 'draft can edit');
assert(eventAdminActionFlags('PUBLISHED').canUnpublish, 'published can unpublish');
assert(eventAdminActionFlags('PUBLISHED').canFinish, 'published can finish');
assert(eventAdminActionFlags('PUBLISHED').canCancel, 'published can cancel');
assert(!eventAdminActionFlags('PUBLISHED').canPublish, 'published cannot publish');
assert(!eventAdminActionFlags('DRAFT').canFinish, 'draft cannot finish');
assert(!eventAdminActionFlags('FINISHED').canPublish, 'finished cannot publish');
assert(!eventAdminActionFlags('FINISHED').canFinish, 'finished cannot finish');
assert(!eventAdminActionFlags('FINISHED').canEdit, 'finished cannot edit');
assert(!eventAdminActionFlags('CANCELLED').canUnpublish, 'cancelled cannot unpublish');
assert(!eventAdminActionFlags('CANCELLED').canCancel, 'cancelled cannot cancel again');
assert(!eventAdminActionFlags('CANCELLED').canEdit, 'cancelled cannot edit');
assert(eventStatusClass('PUBLISHED') === 'is-published', 'published class');
assert(eventStatusClass('FINISHED') === 'is-finished', 'finished class');
assert(eventStatusClass('CANCELLED') === 'is-cancelled', 'cancelled class');
assert(eventStatusClass('DRAFT') === 'is-draft', 'draft class');
console.log('event-admin-actions spec OK');
