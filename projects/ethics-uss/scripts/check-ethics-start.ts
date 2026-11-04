import { readFileSync } from 'node:fs';
import { ethicsStartOutcome } from '../src/app/core/auth/ethics-start-outcome.ts';

function check(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

check(ethicsStartOutcome(true, 'ETHICS_INVITE_SENT', true) === 'sent', 'a sent invitation is success');
check(ethicsStartOutcome(true, 'ETHICS_INVITE_RESENT', true) === 'resent', 'a resent invitation is a new link');
check(ethicsStartOutcome(true, 'ETHICS_INVITE_SENT', false) === 'mail', 'a failed send is not success');
check(ethicsStartOutcome(true, 'ETHICS_INVITE_RESENT', false) === 'mail', 'a failed resend is not success');
check(ethicsStartOutcome(true, 'ETHICS_USE_LOGIN', false) === 'login', 'an existing account goes to sign in');
check(ethicsStartOutcome(false, 'ACCOUNT_EXISTS', false) === 'login', 'an existing-account rejection is not a mail success');
check(ethicsStartOutcome(false, 'NOT_INSTITUTIONAL_DOMAIN', false) === 'domain', 'the backend domain code keeps the current message');
check(ethicsStartOutcome(true, 'ETHICS_SOMETHING', true) === 'rejected', 'an unknown success code is not treated as an invitation');
check(ethicsStartOutcome(false, 'EMAIL_INVALID', false) === 'rejected', 'another rejection is not the institutional sentence');

const page = readFileSync(new URL('../src/app/features/auth/role-select.page.ts', import.meta.url), 'utf8');
const i18n = readFileSync(new URL('./build-i18n.mjs', import.meta.url), 'utf8');
check(page.includes('accountEmail'), 'changing the email clears the previous rejection');
check(page.includes("result === 'domain'"), 'a rejected domain still maps to the institutional sentence');
check(page.includes("accountResult() === 'sent'"), 'a sent invitation replaces the form');
check(page.includes('auth.checkEmailTitle'), 'the success state has a title');
check(page.includes('auth.invitationSent'), 'a new invitation explains the email');
check(page.includes('auth.invitationResent'), 'a resent invitation says a new link was sent');
check(page.includes('auth.useExistingLogin'), 'an existing account keeps its own message');
check(page.includes('auth.invitationMailFailed'), 'a mail failure stays an error');
check(page.includes('role="status"'), 'success is not announced only as an alert');
check(!page.includes('auth.institutionalNextSteps'), 'create account no longer uses the generic next-step sentence');
const start = page.indexOf('async createAccount');
const next = page.indexOf('async leaveDenied');
const method = page.slice(start, next);
check(!method.includes('isAllowedInstitutionalEmail'), 'create account does not reject Gmail before the request');
check(!method.includes('auth.useExistingLogin'), 'create account does not assign the existing-account text itself');
check(i18n.includes('Te enviamos un enlace para completar tu registro y activar tu acceso a Ética de Proyectos.'), 'the sent copy is the product sentence');
check(i18n.includes('Ya existe una cuenta asociada a este correo.'), 'the existing-account copy names sign in');

console.log('check-ethics-start OK');
