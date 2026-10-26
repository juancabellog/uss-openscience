import { classifyPublicEventFailure } from './public-event-state';

function assert(condition: unknown, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

export function runPublicEventStateTests(): void {
  assert(classifyPublicEventFailure(0) === 'offline', 'offline');
  assert(classifyPublicEventFailure(404, 'EVENT_NOT_FOUND') === 'missing', 'missing');
  assert(classifyPublicEventFailure(403, 'EVENT_NOT_PUBLISHED') === 'unpublished', 'unpublished');
  assert(classifyPublicEventFailure(404) === 'missing', 'legacy 404');
  assert(classifyPublicEventFailure(500) === 'error', 'unexpected');
}

runPublicEventStateTests();
