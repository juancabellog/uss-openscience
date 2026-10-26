import { RegisterRequestDto, SAFE_AUTH_PUBLIC_CONFIG } from '../../core/services/api/auth-api.types';
import { isPublicAuthApi, isPublicPagePath, shouldRedirectUnauthorized } from '../../core/auth/auth-public.util';
import { registerEmailLabelKey, registerEmailPlaceholderKey, resultMessageKey } from './register.component';

describe('self registration contract', () => {
  it('exposes a public register route', () => {
    expect(isPublicPagePath('/auth/register')).toBe(true);
    expect(isPublicPagePath('/register')).toBe(true);
  });

  it('does not redirect a 401 from the public register API', () => {
    expect(isPublicAuthApi('/api/v1/auth/register')).toBe(true);
    expect(shouldRedirectUnauthorized('/api/v1/auth/register', '/auth/register')).toBe(false);
  });

  it('sends only first name, last name and email', () => {
    const body: RegisterRequestDto = {
      firstName: 'Juan',
      lastName: 'Pérez',
      email: 'juan.perez@uss.cl'
    };
    expect(Object.keys(body).sort()).toEqual(['email', 'firstName', 'lastName']);
    expect('password' in body).toBe(false);
    expect('roleIds' in body).toBe(false);
    expect('status' in body).toBe(false);
    expect('permissions' in body).toBe(false);
  });

  it('adapts label and placeholder to the public registration policy', () => {
    expect(registerEmailLabelKey(true)).toBe('auth.institutionalEmailLabel');
    expect(registerEmailPlaceholderKey(true)).toBe('auth.institutionalEmailPlaceholder');
    expect(registerEmailLabelKey(false)).toBe('auth.email');
    expect(registerEmailPlaceholderKey(false)).toBe('auth.emailPlaceholder');
    expect(SAFE_AUTH_PUBLIC_CONFIG.registrationUssOnly).toBe(true);
  });

  it('treats auth config as a public API', () => {
    expect(isPublicAuthApi('/api/v1/auth/config')).toBe(true);
    expect(shouldRedirectUnauthorized('/api/v1/auth/config', '/auth/register')).toBe(false);
  });

  it('maps duplicate outcomes without revealing a disabled account', () => {
    expect(resultMessageKey({ code: 'AUTH_REGISTER_EXISTS', message: '', outcome: 'EXISTS_ACTIVE' })).toBe(
      'auth.registerExistsActive'
    );
    expect(resultMessageKey({ code: 'AUTH_REGISTER_PENDING', message: '', outcome: 'EXISTS_PENDING' })).toBe(
      'auth.registerExistsPending'
    );
    expect(resultMessageKey({ code: 'AUTH_REGISTER_UNAVAILABLE', message: '', outcome: 'EXISTS_UNAVAILABLE' })).toBe(
      'auth.registerUnavailable'
    );
  });
});
