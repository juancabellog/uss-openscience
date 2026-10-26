import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { DemoAuthService } from '../auth/demo-auth.service';
import { Role } from '../models/ethics.models';

export const demoAuthGuard: CanActivateFn = () => {
  const auth = inject(DemoAuthService);
  const router = inject(Router);
  return auth.currentUser() ? true : router.parseUrl('/ingresar');
};

export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(DemoAuthService);
    const router = inject(Router);
    return auth.hasRole(...roles) ? true : router.parseUrl('/');
  };
}
