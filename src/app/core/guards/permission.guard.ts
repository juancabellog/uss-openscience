import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AppModuleCode, AppPermissionCode } from '../models/openscience.models';
import { AuthService } from '../services/auth.service';

export const permissionGuard = (
  module: AppModuleCode,
  permission: AppPermissionCode = 'CONSULT'
): CanActivateFn => {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }
    return auth.can(module, permission) ? true : router.createUrlTree(['/unauthorized']);
  };
};
