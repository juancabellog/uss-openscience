export type LoginFailure = 'invalid' | 'pending' | 'locked' | 'unavailable';
export type EthicsEntry = 'login' | 'enter' | 'denied';

/** Un 2xx no es un fallo. 401 es credencial, no falta de acceso a Ethics. */
export function classifyLoginResponse(status: number, code: string | null): LoginFailure | null {
  if (status >= 200 && status < 300) {
    return null;
  }
  const normalized = (code ?? '').toUpperCase();
  if (normalized === 'AUTH_PENDING') {
    return 'pending';
  }
  if (normalized === 'AUTH_LOCKED') {
    return 'locked';
  }
  if (status === 401 || normalized === 'AUTH_INVALID' || normalized === 'AUTH_RATE_LIMITED') {
    return 'invalid';
  }
  return 'unavailable';
}

/**
 * Sin sesión se muestra el login de Ethics.
 * Con sesión, un dominio institucional entra y el backend aprovisiona si falta Ethics.
 * Un externo sin Ethics activo queda denegado.
 */
export function ethicsEntry(input: {
  authenticated: boolean;
  ethicsActive: boolean;
  institutional: boolean;
}): EthicsEntry {
  if (!input.authenticated) {
    return 'login';
  }
  if (input.ethicsActive || input.institutional) {
    return 'enter';
  }
  return 'denied';
}
