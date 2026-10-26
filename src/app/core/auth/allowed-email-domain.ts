export const ACCOUNT_ALLOWED_EMAIL_DOMAINS = ['uss.cl'] as const;

export function normalizeEmail(value: string | null | undefined): string {
  return (value ?? '').trim().toLowerCase();
}

export function emailDomain(value: string | null | undefined): string {
  const email = normalizeEmail(value);
  const at = email.lastIndexOf('@');
  return at > 0 ? email.slice(at + 1) : '';
}

export function isAllowedEmailDomain(
  value: string | null | undefined,
  domains: readonly string[] = ACCOUNT_ALLOWED_EMAIL_DOMAINS
): boolean {
  const domain = emailDomain(value);
  return domain.length > 0 && domains.includes(domain);
}

export function isRegistrationEmailAllowed(
  value: string | null | undefined,
  ussOnly: boolean,
  domains: readonly string[] = ACCOUNT_ALLOWED_EMAIL_DOMAINS
): boolean {
  const email = normalizeEmail(value);
  if (!email || !email.includes('@')) {
    return false;
  }
  return !ussOnly || isAllowedEmailDomain(email, domains);
}

export function normalizePersonName(value: string | null | undefined): string {
  return (value ?? '').trim().replace(/\s+/g, ' ');
}
