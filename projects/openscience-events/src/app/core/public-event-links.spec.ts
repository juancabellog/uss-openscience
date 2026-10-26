import {
  buildPublicEventUrl,
  buildPublicRegistrationUrl,
  buildPublicVerificationUrl,
  normalizePublicBase
} from './public-event-url.ts';

function assert(condition: unknown, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

export function runPublicEventLinksTests(): void {
  assert(
    normalizePublicBase('/events/', 'https://apps.cienciavida.org') === 'https://apps.cienciavida.org/events/',
    'relative production base'
  );
  assert(
    normalizePublicBase('https://apps.cienciavida.org/events', '') === 'https://apps.cienciavida.org/events/',
    'absolute base gains trailing slash'
  );
  assert(
    buildPublicRegistrationUrl('http://localhost:4300/', 'evento-test') ===
      'http://localhost:4300/eventos/evento-test/inscripcion',
    'local registration url'
  );
  assert(
    buildPublicEventUrl('http://localhost:4300/', 'evento-test') === 'http://localhost:4300/eventos/evento-test',
    'local public url'
  );
  assert(
    buildPublicRegistrationUrl('https://apps.cienciavida.org/events/', 'evento-test') ===
      'https://apps.cienciavida.org/events/eventos/evento-test/inscripcion',
    'production registration url'
  );
  assert(
    buildPublicEventUrl('https://apps.cienciavida.org/events', 'evento-test') ===
      'https://apps.cienciavida.org/events/eventos/evento-test',
    'production public url without trailing slash'
  );
  assert(
    buildPublicRegistrationUrl('/events/', 'evento-test', 'https://apps.cienciavida.org') ===
      'https://apps.cienciavida.org/events/eventos/evento-test/inscripcion',
    'base href plus current origin'
  );
  assert(
    !buildPublicRegistrationUrl('https://apps.cienciavida.org/events/', 'evento-test').includes(
      '/events/events/'
    ),
    'no doubled events path'
  );
  const encoded = buildPublicVerificationUrl(
    'https://apps.cienciavida.org/events/',
    'evento-test',
    'ab+c=/d'
  );
  assert(encoded.includes('token=ab%2Bc%3D%2Fd') || encoded.includes('token=ab%2Bc%3D%2Fd'), 'encoded token');
  assert(!encoded.includes('localhost'), 'production verification has no localhost');
}

runPublicEventLinksTests();
