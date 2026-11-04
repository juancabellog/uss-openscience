import { readFileSync } from 'node:fs';
import { activationFailureKey, activationFields } from './auth-public.util.ts';

function check(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

const passwordOnly = activationFields(false);
check(passwordOnly.length === 2 && passwordOnly.includes('password') && passwordOnly.includes('confirm'), 'profileRequired false shows password and confirmation');
check(!passwordOnly.includes('givenName') && !passwordOnly.includes('campus'), 'profileRequired false hides the institutional profile');

const profile = activationFields(true);
for (const field of ['givenName', 'familyName', 'campus', 'faculty', 'positionTitle', 'password', 'confirm']) {
  check(profile.includes(field), `profileRequired true shows ${field}`);
}

check(activationFailureKey('AUTH_PROFILE_REQUIRED') === 'auth.activateProfileRejected', 'a missing profile is not an expired link');
check(activationFailureKey('AUTH_PROFILE_REQUIRED') !== 'auth.tokenInvalid', 'AUTH_PROFILE_REQUIRED does not use the invalid-link text');
check(activationFailureKey('AUTH_TOKEN_INVALID') === 'auth.tokenInvalid', 'a real token failure keeps the unavailable-link text');
check(activationFailureKey('AUTH_PASSWORD_POLICY') === 'auth.passwordPolicy', 'a password rule stays a password rule');

const screen = readFileSync(new URL('../../features/auth/activate-account.component.html', import.meta.url), 'utf8');
const logic = readFileSync(new URL('../../features/auth/activate-account.component.ts', import.meta.url), 'utf8');
for (const field of ['activate-given', 'activate-family', 'activate-campus', 'activate-faculty', 'activate-position', 'activate-password', 'activate-confirm']) {
  check(screen.includes(field), `the OpenScience activation screen includes ${field}`);
}
check(screen.includes('preview().profileRequired'), 'profile fields follow the GET flag');
check(screen.includes('auth.activateEthicsTitle') && screen.includes('auth.activateEthicsHelp') && screen.includes('auth.activateLocalIntro'), 'an Ethics invitation does not use the OpenScience account sentence');
check(screen.includes("ethics() && preview().profileRequired"), 'the non-PURE Ethics intro follows the GET flag');
const spanish = readFileSync(new URL('../i18n/es.json', import.meta.url), 'utf8');
check(spanish.includes('Completa tus datos personales e institucionales para activar tu acceso.'), 'the non-PURE Ethics screen asks for the local profile');
check(spanish.includes('Activar mi acceso'), 'the Ethics action is not the OpenScience activate-account label');
check(screen.includes('auth.tokenExpired') && screen.includes('auth.tokenUsed') && screen.includes('auth.tokenInvalid'), 'GET token states keep their own messages');
check(logic.includes('activationFailureKey'), 'POST errors are mapped by domain code');
check(!logic.includes("error.set('auth.tokenInvalid')"), 'a validation error does not reuse the invalid-link text');
check(logic.includes('givenName') && logic.includes('positionTitle') && logic.includes('faculty'), 'the POST sends the profile fields the backend already accepts');

console.log('check-activation-form OK');
