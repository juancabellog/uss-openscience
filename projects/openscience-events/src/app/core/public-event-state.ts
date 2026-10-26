import { HttpErrorResponse } from '@angular/common/http';
import { EventPublicDetail } from './events-api.service';

export type PublicEventKind = 'loading' | 'ok' | 'missing' | 'unpublished' | 'offline' | 'error';

export type PublicEventView =
  | { kind: 'loading'; event: null }
  | { kind: 'ok'; event: EventPublicDetail }
  | { kind: 'missing'; event: null }
  | { kind: 'unpublished'; event: null }
  | { kind: 'offline'; event: null }
  | { kind: 'error'; event: null };

export function httpErrorCode(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const body = error.error;
  if (body && typeof body === 'object' && 'code' in body && typeof body.code === 'string') {
    return body.code;
  }
  return null;
}

export function classifyPublicEventFailure(status: number, code?: string | null): Exclude<PublicEventKind, 'loading' | 'ok'> {
  if (status === 0) {
    return 'offline';
  }
  if (code === 'EVENT_NOT_PUBLISHED' || status === 403) {
    return 'unpublished';
  }
  if (code === 'EVENT_NOT_FOUND' || status === 404) {
    return 'missing';
  }
  return 'error';
}

export function publicEventFailure(error: unknown): PublicEventView {
  const status = error instanceof HttpErrorResponse ? error.status : 0;
  const kind = classifyPublicEventFailure(status, httpErrorCode(error));
  return { kind, event: null };
}
