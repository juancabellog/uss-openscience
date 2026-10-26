/**
 * Public event links must follow the SPA location, not the API host.
 * Production is served at /events/; local serve uses / on :4300.
 */
export function normalizePublicBase(baseHrefOrUri: string, origin = ''): string {
  const raw = (baseHrefOrUri || '/').trim() || '/';
  if (/^https?:\/\//i.test(raw)) {
    return raw.endsWith('/') ? raw : `${raw}/`;
  }
  const host = origin.replace(/\/+$/, '');
  const path = raw.startsWith('/') ? raw : `/${raw}`;
  const joined = `${host}${path}`;
  return joined.endsWith('/') ? joined : `${joined}/`;
}

export function buildPublicEventUrl(baseHrefOrUri: string, slug: string, origin = ''): string {
  return new URL(`eventos/${slug}`, normalizePublicBase(baseHrefOrUri, origin)).href.replace(/\/+$/, '');
}

export function buildPublicRegistrationUrl(baseHrefOrUri: string, slug: string, origin = ''): string {
  return new URL(`eventos/${slug}/inscripcion`, normalizePublicBase(baseHrefOrUri, origin)).href;
}

export function buildPublicVerificationUrl(
  baseHrefOrUri: string,
  slug: string,
  token: string,
  origin = ''
): string {
  const url = new URL(buildPublicRegistrationUrl(baseHrefOrUri, slug, origin));
  url.searchParams.set('token', token);
  return url.href;
}
