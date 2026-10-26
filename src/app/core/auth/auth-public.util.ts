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
