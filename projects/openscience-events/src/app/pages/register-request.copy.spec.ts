import { registerRequestCopy, registerRequestErrorKey } from './register-request.copy.ts';

function assert(condition: unknown, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

export function runRegisterRequestCopyTests(): void {
  const sent = registerRequestCopy(true);
  assert(sent.bodyKey === 'register.checkEmailSent', 'sent body');
  assert(sent.toastKey === 'register.toastSent', 'sent toast');

  const processed = registerRequestCopy(false);
  assert(processed.bodyKey === 'register.checkEmailProcessed', 'processed body');
  assert(processed.toastKey === 'register.toastProcessed', 'processed toast');

  assert(registerRequestErrorKey(0) === 'detail.connectionError', 'connection');
  assert(registerRequestErrorKey(500, 'EVENT_MAIL_FAILED') === 'errors.EVENT_MAIL_FAILED', 'smtp');
  assert(registerRequestErrorKey(503) === 'register.sendFailed', 'generic');
}

runRegisterRequestCopyTests();
