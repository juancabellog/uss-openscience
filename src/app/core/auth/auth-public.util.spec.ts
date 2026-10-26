import {
  currentPagePath,
  isPublicAuthApi,
  isPublicPagePath,
  shouldRedirectUnauthorized
} from './auth-public.util';

describe('auth public flow', () => {
  it('treats activate-account as a public page without login', () => {
    expect(isPublicPagePath('/auth/activate-account')).toBe(true);
    expect(isPublicPagePath('/auth/activate-account?token=abc')).toBe(true);
    expect(isPublicPagePath('/activate-account')).toBe(true);
    expect(isPublicPagePath('/openscience/auth/activate-account')).toBe(true);
    expect(isPublicPagePath('/openscience/auth/activate-account?token=abc')).toBe(true);
  });

  it('does not treat activate as a protected page', () => {
    expect(isPublicPagePath('/people')).toBe(false);
    expect(isPublicPagePath('/')).toBe(false);
  });

  it('does not redirect activation validation on 401', () => {
    expect(shouldRedirectUnauthorized('/api/v1/auth/activate?token=x', '/auth/activate-account')).toBe(false);
    expect(shouldRedirectUnauthorized('/openscience-api/v1/auth/activate?token=x', '/openscience/auth/activate-account')).toBe(false);
    expect(shouldRedirectUnauthorized('/api/v1/auth/me', '/auth/activate-account')).toBe(false);
    expect(shouldRedirectUnauthorized('/api/v1/auth/me', '/')).toBe(false);
  });

  it('still redirects protected API 401 on protected pages', () => {
    expect(shouldRedirectUnauthorized('/api/v1/people', '/people')).toBe(true);
  });

  it('uses the browser path during bootstrap when router url is still /', () => {
    expect(currentPagePath('/', '/auth/activate-account')).toBe('/auth/activate-account');
    expect(currentPagePath('/', '/openscience/auth/activate-account', '/openscience/')).toBe(
      '/auth/activate-account'
    );
  });

  it('recognizes public auth APIs', () => {
    expect(isPublicAuthApi('/api/v1/auth/activate')).toBe(true);
    expect(isPublicAuthApi('/api/v1/auth/me')).toBe(true);
    expect(isPublicAuthApi('/api/v1/auth/register')).toBe(true);
    expect(isPublicAuthApi('/api/v1/people')).toBe(false);
  });

  it('treats register as a public page without login', () => {
    expect(isPublicPagePath('/auth/register')).toBe(true);
    expect(shouldRedirectUnauthorized('/api/v1/auth/register', '/auth/register')).toBe(false);
  });
});
