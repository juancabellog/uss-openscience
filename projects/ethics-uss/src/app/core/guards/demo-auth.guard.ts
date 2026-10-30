import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { DemoAuthService } from '../auth/demo-auth.service';
import { demoSelectorVisible } from '../auth/demo-mode';
import { SessionIdentityService } from '../auth/session-identity.service';
import { Role } from '../models/ethics.models';
import { PermissionService } from '../services/permission.service';
import { ethicsOpen } from '../services/user-admin.service';

export const demoAuthGuard: CanActivateFn = () => {
  const router = inject(Router);
  if (!demoSelectorVisible(environment.demoEnabled, environment.production)) {
    const session = inject(SessionIdentityService);
    return session.ensure().then((user) => (user ? true : router.parseUrl('/ingresar')));
  }
  const auth = inject(DemoAuthService);
  const user = auth.currentUser();
  if (!user || !ethicsOpen(user)) {
    return router.parseUrl(user ? '/ingresar?acceso=denegado' : '/ingresar');
  }
  return true;
};

const ROUTE_CAPABILITIES: readonly { prefix: string; capability: string }[] = [
  { prefix: '/proyectos/nuevo', capability: 'CREATE_REQUEST' },
  { prefix: '/solicitudes', capability: 'VIEW_PENDING_CLASSIFICATION' },
  { prefix: '/sesiones', capability: 'VIEW_COMMITTEE_SESSIONS' },
  { prefix: '/revisores', capability: 'VIEW_REVIEWER_POOL' },
  { prefix: '/vencimientos', capability: 'VIEW_EXPIRATIONS' },
  { prefix: '/auditoria', capability: 'VIEW_AUDIT' },
  { prefix: '/comites', capability: 'VIEW_CONFIGURATION' },
  { prefix: '/configuracion', capability: 'VIEW_CONFIGURATION' },
  { prefix: '/usuarios', capability: 'MANAGE_USERS' }
];

export function capabilityForUrl(url: string): string | null {
  const path = url.split('?')[0].split('#')[0];
  return ROUTE_CAPABILITIES.find((item) => path === item.prefix || path.startsWith(`${item.prefix}/`))?.capability ?? null;
}

export function capabilityGuard(capability: string): CanActivateFn {
  return () => {
    const auth = inject(DemoAuthService);
    const permissions = inject(PermissionService);
    const router = inject(Router);
    return permissions.can(auth.currentUser(), capability) ? true : router.parseUrl('/');
  };
}

export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(DemoAuthService);
    const router = inject(Router);
    return auth.hasRole(...roles) ? true : router.parseUrl('/');
  };
}
