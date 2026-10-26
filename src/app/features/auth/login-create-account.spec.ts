import { isPublicPagePath } from '../../core/auth/auth-public.util';

describe('login create account', () => {
  it('keeps Crear cuenta as a secondary public register action', () => {
    expect(isPublicPagePath('/auth/register')).toBe(true);
  });
});
