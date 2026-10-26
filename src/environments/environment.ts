export type EnvironmentKind = 'DEV' | 'QA' | 'PROD';

export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
  /** true = el resto de módulos usa mocks. Dashboard, Personas y Producción científica llaman al backend siempre. */
  useMockData: true,
  appName: 'OPENSCIENCE-USS',
  appDescriptor: 'Ciencia Abierta USS',
  appVersion: '0.1.0',
  ussLogoUrl: 'assets/branding/USShorizontal-tagline-ilumina-dark.svg',
  brandingLogoUrl: 'assets/branding/logo%20openscience.png',
  environmentKind: 'DEV' as EnvironmentKind,
  environmentBadge: 'DEV · MIXED'
};
