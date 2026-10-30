/** Misma lista que InstitutionalDomains en el backend. No usar endsWith('uss.cl'). */
export const ALLOWED_SELF_REGISTRATION_DOMAINS = ['uss.cl', 'correo.uss.cl', 'docente.uss.cl'] as const;

export function institutionalDomain(email: string | null | undefined): string | null {
  const normalized = email?.trim().toLowerCase();
  if (!normalized) {
    return null;
  }
  const at = normalized.indexOf('@');
  if (at <= 0 || at !== normalized.lastIndexOf('@') || at === normalized.length - 1) {
    return null;
  }
  const domain = normalized.slice(at + 1);
  if (domain.includes(' ') || domain.startsWith('.') || domain.endsWith('.') || !domain.includes('.')) {
    return null;
  }
  return domain;
}

export function isAllowedInstitutionalEmail(email: string | null | undefined): boolean {
  const domain = institutionalDomain(email);
  return domain != null && (ALLOWED_SELF_REGISTRATION_DOMAINS as readonly string[]).includes(domain);
}
