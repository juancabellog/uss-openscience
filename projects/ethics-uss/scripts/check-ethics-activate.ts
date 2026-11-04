import { readFileSync } from 'node:fs';

function check(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

const page = readFileSync(new URL('../src/app/features/auth/activate-account.page.ts', import.meta.url), 'utf8');
const routes = readFileSync(new URL('../src/app/app.routes.ts', import.meta.url), 'utf8');
const i18n = readFileSync(new URL('./build-i18n.mjs', import.meta.url), 'utf8');
check(routes.includes("path: 'activar-cuenta'"), 'Ethics exposes the activation route');
check(page.includes('routerLink="/ingresar"'), 'after activation the person returns to the Ethics sign-in');
check(!page.includes('/login'), 'the Ethics activation page does not link to OpenScience sign-in');
check(page.includes('activate-given') && page.includes('activate-campus') && page.includes('activate-faculty') && page.includes('activate-position'), 'a non-PURE invitation shows the institutional profile');
check(page.includes('profileRequired'), 'the profile follows the GET flag');
check(i18n.includes('Activar acceso a Ética de Proyectos'), 'the page title belongs to Ethics');
check(i18n.includes('Completa tus datos personales e institucionales para activar tu acceso.'), 'the non-PURE copy asks for the local profile');

console.log('check-ethics-activate OK');
