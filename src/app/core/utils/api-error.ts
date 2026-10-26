import { HttpErrorResponse } from '@angular/common/http';

export function apiErrorCode(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse) || error.error == null || typeof error.error !== 'object') {
    return null;
  }
  const code = (error.error as { code?: unknown }).code;
  return typeof code === 'string' && code.trim() !== '' ? code.trim() : null;
}
