const PUBLIC_PAGE_PATHS = [
  '/login',
  '/register',
  '/forgot-password',
  '/reset-password',
  '/activate-account',
  '/auth/register',
  '/auth/activate-account',
  '/auth/forgot-password',
  '/auth/reset-password'
];

const PUBLIC_AUTH_API = /\/auth\/(login|me|register|config|forgot-password|reset-password|activate|resend-activation)(\/|\?|$)/;

export function isPublicPagePath(url: string | null | undefined): boolean {
  const path = stripAppBase((url ?? '').split('?')[0] || '/');
  return PUBLIC_PAGE_PATHS.some(
    (item) => path === item || path.startsWith(`${item}/`) || path.endsWith(item)
  );
}

export function isPublicAuthApi(url: string | null | undefined): boolean {
  return PUBLIC_AUTH_API.test(url ?? '');
}

export function shouldRedirectUnauthorized(requestUrl: string, pageUrl: string): boolean {
  return !isPublicAuthApi(requestUrl) && !isPublicPagePath(pageUrl);
}

/**
 * El POST de activación usa el mismo texto de enlace inválido solo cuando el token
 * no sirve. Un perfil institucional incompleto no consume ni invalida el token.
 */
export const ACTIVATION_PROFILE_FIELDS = [
  'givenName',
  'familyName',
  'campus',
  'faculty',
  'positionTitle',
  'password',
  'confirm'
] as const;

export const ACTIVATION_PASSWORD_FIELDS = ['password', 'confirm'] as const;

export function activationFields(profileRequired: boolean | undefined): readonly string[] {
  return profileRequired === true ? ACTIVATION_PROFILE_FIELDS : ACTIVATION_PASSWORD_FIELDS;
}

export function activationFailureKey(code: string | null | undefined): string {
  switch (code) {
    case 'AUTH_PROFILE_REQUIRED':
      return 'auth.activateProfileRejected';
    case 'AUTH_ALREADY_ACTIVE':
      return 'auth.alreadyActivated';
    case 'AUTH_PASSWORD_POLICY':
    case 'AUTH_PASSWORD_REQUIRED':
      return 'auth.passwordPolicy';
    case 'AUTH_TOKEN_INVALID':
      return 'auth.tokenInvalid';
    default:
      return 'auth.activateFailed';
  }
}

export function currentPagePath(routerUrl: string, locationPath?: string, baseHref?: string): string {
  const fromLocation = stripAppBase((locationPath ?? '').split('?')[0], baseHref);
  if (fromLocation && fromLocation !== '/') {
    return fromLocation;
  }
  return stripAppBase((routerUrl ?? '/').split('?')[0] || '/', baseHref);
}

/** Tomcat serves the SPA under /openscience/; router URLs do not include that prefix. */
export function stripAppBase(path: string, baseHref?: string): string {
  const pathname = (path || '/').split('?')[0] || '/';
  const base = (baseHref ?? readBaseHref()).replace(/\/+$/, '');
  if (base && (pathname === base || pathname.startsWith(base + '/'))) {
    const stripped = pathname.slice(base.length) || '/';
    return stripped.startsWith('/') ? stripped : `/${stripped}`;
  }
  return pathname || '/';
}

function readBaseHref(): string {
  if (typeof document === 'undefined') {
    return '';
  }
  const href = document.querySelector('base')?.getAttribute('href')?.trim();
  if (!href || href === '/') {
    return '';
  }
  return href.replace(/\/+$/, '');
}
