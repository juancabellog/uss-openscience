export function registerRequestCopy(emailSent: boolean): { bodyKey: string; toastKey: string } {
  return emailSent
    ? { bodyKey: 'register.checkEmailSent', toastKey: 'register.toastSent' }
    : { bodyKey: 'register.checkEmailProcessed', toastKey: 'register.toastProcessed' };
}

export function registerRequestErrorKey(status: number, code?: string | null): string {
  if (status === 0) {
    return 'detail.connectionError';
  }
  if (code) {
    return `errors.${code}`;
  }
  return 'register.sendFailed';
}
