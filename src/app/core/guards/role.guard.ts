import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

/**
 * Deprecated leftover. Role names must never authorize UI or routes.
 * Effective permissions are the only authorization source.
 */
export const roleGuard = (_roleCodes: string[]): CanActivateFn => {
  return () => inject(Router).createUrlTree(['/unauthorized']);
};
