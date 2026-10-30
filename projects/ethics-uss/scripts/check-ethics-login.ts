import { readFileSync } from 'node:fs';
import { classifyLoginResponse, ethicsEntry } from '../src/app/core/auth/ethics-entry.ts';

function check(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

check(ethicsEntry({ authenticated: false, ethicsActive: false, institutional: true }) === 'login', 'A: an institutional user without a session sees the Ethics login');
check(ethicsEntry({ authenticated: true, ethicsActive: true, institutional: true }) === 'enter', 'B: an existing session enters without another login');
check(ethicsEntry({ authenticated: true, ethicsActive: false, institutional: true }) === 'enter', 'C: an institutional account can be provisioned after login');
check(ethicsEntry({ authenticated: true, ethicsActive: false, institutional: false }) === 'denied', 'D: an external account without Ethics is denied');
check(ethicsEntry({ authenticated: true, ethicsActive: true, institutional: false }) === 'enter', 'E: an enabled external reviewer enters');
check(classifyLoginResponse(401, 'AUTH_INVALID') === 'invalid', 'F: bad credentials stay an authentication error');
check(classifyLoginResponse(403, 'ETHICS_ACCESS_DENIED') === 'unavailable', 'a 403 is not reported as a wrong password');
check(classifyLoginResponse(200, null) === null, 'a successful login is not an error');

const page = readFileSync(new URL('../src/app/features/auth/role-select.page.ts', import.meta.url), 'utf8');
const signIn = readFileSync(new URL('../src/app/core/auth/shared-session-sign-in.ts', import.meta.url), 'utf8');
check(!page.includes('Iniciar sesión en OpenScience'), 'the login screen does not send the user to OpenScience');
check(!page.includes('openscienceOrigin'), 'the login screen does not navigate to the other application');
check(page.includes('auth.signIn'), 'the button is the Ethics sign-in action');
const loginCard = page.slice(page.indexOf('} @else {'), page.indexOf('preventDefault(); signIn()'));
const cardAt = loginCard.indexOf('eth-login__card');
const switcherAt = loginCard.indexOf('eth-language-switcher');
const titleAt = loginCard.indexOf('brand.full');
check(cardAt >= 0 && switcherAt > cardAt && switcherAt < titleAt, 'the language switcher sits inside the card, above the title');
check(!loginCard.slice(0, cardAt).includes('eth-language-switcher'), 'the language switcher is not outside the card');
const signInView = page.slice(page.indexOf('preventDefault(); signIn()'));
check(signInView.includes('eth-login__switch') && signInView.split('auth.ussOnly').length - 1 === 1, 'the sign-in view mentions the USS restriction only next to create account');
const i18n = readFileSync(new URL('./build-i18n.mjs', import.meta.url), 'utf8');
check(i18n.includes('Utiliza las credenciales asociadas a tu cuenta.'), 'the credential hint does not say the login is USS-only');
check(!i18n.includes('OpenScience/USS'), 'the credential hint does not append /USS');
check(page.includes("panel() === 'denied'"), 'a signed-in user without Ethics sees a denial, not the credential error');
check(signIn.includes('/auth') && signIn.includes('/login'), 'sign-in uses the shared auth endpoint');
check(signIn.includes("credentials: 'include'"), 'the shared cookie is reused');
check(!signIn.includes('password_hash'), 'Ethics does not store a password');
const passwordField = page.slice(page.indexOf('auth.password'), page.indexOf('eth-login__forgot'));
check(page.includes('readonly showPassword = signal(false)'), 'the password starts hidden');
check(passwordField.includes("showPassword() ? 'text' : 'password'"), 'the eye toggles the input type');
check(passwordField.includes('type="button"'), 'the eye is a button and does not submit the form');
check(passwordField.includes('autocomplete="current-password"'), 'login keeps current-password autocomplete');
check(passwordField.includes('auth.showPassword') && passwordField.includes('auth.hidePassword'), 'the eye label changes with visibility');
check(passwordField.includes("showPassword() ? 'eye-off' : 'eye'"), 'the eye icon follows the visibility state');
check(passwordField.includes('(ngModelChange)="password.set($event)"'), 'typing still updates the password');
const toggle = passwordField.slice(passwordField.indexOf('<button'));
check(toggle.includes('showPassword.update') && !toggle.includes('password.set'), 'the eye does not rewrite the password');
check(i18n.includes("p('auth.showPassword', 'Mostrar contraseña', 'Show password')"), 'show password is translated');
check(i18n.includes("p('auth.hidePassword', 'Ocultar contraseña', 'Hide password')"), 'hide password is translated');
const production = readFileSync(new URL('../src/environments/environment.production.ts', import.meta.url), 'utf8');
const development = readFileSync(new URL('../src/environments/environment.ts', import.meta.url), 'utf8');
check(production.includes("apiBaseUrl: '/openscience-api/v1'"), 'production login uses the public OpenScience API path');
check(production.includes("futureApiBaseUrl: '/openscience-api/v1/ethics'"), 'production ethics calls use the public OpenScience API path');
check(development.includes("apiBaseUrl: '/api/v1'"), 'development keeps the local proxy path');

console.log('check-ethics-login OK');
