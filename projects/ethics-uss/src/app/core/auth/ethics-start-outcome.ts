export type InstitutionalStart = 'sent' | 'resent' | 'login' | 'domain' | 'mail' | 'rejected';

/**
 * El alta pública distingue la respuesta de POST /auth/ethics-start.
 * Un HTTP 200 no basta: la invitación solo es éxito cuando el código y emailSent lo dicen.
 */
export function ethicsStartOutcome(ok: boolean, code: string | null, emailSent: boolean): InstitutionalStart {
  if (code === 'ETHICS_USE_LOGIN' || code === 'ACCOUNT_EXISTS') {
    return 'login';
  }
  if (code === 'NOT_INSTITUTIONAL_DOMAIN') {
    return 'domain';
  }
  if (code === 'ETHICS_INVITE_SENT' || code === 'ETHICS_INVITE_RESENT') {
    if (!emailSent) {
      return 'mail';
    }
    return code === 'ETHICS_INVITE_RESENT' ? 'resent' : 'sent';
  }
  if (!ok || code == null) {
    return 'rejected';
  }
  return 'rejected';
}
