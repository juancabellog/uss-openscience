import {
  ACCOUNT_ALLOWED_EMAIL_DOMAINS,
  emailDomain,
  isAllowedEmailDomain,
  isRegistrationEmailAllowed,
  normalizeEmail,
  normalizePersonName
} from './allowed-email-domain';

describe('allowed email domain', () => {
  it('normalizes email to trim and lowercase', () => {
    expect(normalizeEmail('  Juan.Cabello@USS.CL ')).toBe('juan.cabello@uss.cl');
  });

  it('accepts only the institutional USS domain', () => {
    expect(ACCOUNT_ALLOWED_EMAIL_DOMAINS).toEqual(['uss.cl']);
    expect(isAllowedEmailDomain('juan.cabello@uss.cl')).toBe(true);
    expect(isAllowedEmailDomain('Juan.Cabello@USS.CL')).toBe(true);
    expect(isAllowedEmailDomain('juan.cabello@gmail.com')).toBe(false);
    expect(isAllowedEmailDomain('juan.cabello@otros.uss.cl')).toBe(false);
    expect(emailDomain('juan.cabello@uss.cl')).toBe('uss.cl');
  });

  it('applies USS-only only when the registration flag is true', () => {
    expect(isRegistrationEmailAllowed('juan@uss.cl', true)).toBe(true);
    expect(isRegistrationEmailAllowed('Juan@USS.CL', true)).toBe(true);
    expect(isRegistrationEmailAllowed('juan@gmail.com', true)).toBe(false);
    expect(isRegistrationEmailAllowed('juan@uss.cl.evil.com', true)).toBe(false);
    expect(isRegistrationEmailAllowed('juan@gmail.com', false)).toBe(true);
    expect(isRegistrationEmailAllowed('juan@universidad.cl', false)).toBe(true);
  });

  it('keeps compound names after trim', () => {
    expect(normalizePersonName('  De   la   Fuente  ')).toBe('De la Fuente');
    expect(normalizePersonName("O'Connor")).toBe("O'Connor");
    expect(normalizePersonName('Spaudo Valenzuela')).toBe('Spaudo Valenzuela');
  });
});
