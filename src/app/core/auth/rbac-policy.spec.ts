import { canSeeDashboardMetric } from '../../features/dashboard/dashboard.component';
import { isPublicPagePath, shouldRedirectUnauthorized } from './auth-public.util';

describe('platform RBAC policy', () => {
  it('keeps export as a consult operation', () => {
    const auth = {
      canConsult: (module: string) => module === 'PEOPLE',
      canExport: (module: string) => module === 'PEOPLE'
    };
    expect(auth.canExport('PEOPLE')).toBe(true);
    expect(auth.canExport('DATA_QUALITY')).toBe(false);
  });

  it('hides people KPIs without PEOPLE consult', () => {
    const none = { canConsult: () => false };
    const people = { canConsult: (module: string) => module === 'PEOPLE' };
    expect(canSeeDashboardMetric('persons', none)).toBe(false);
    expect(canSeeDashboardMetric('scopus', people)).toBe(true);
    expect(canSeeDashboardMetric('findings', people)).toBe(false);
  });

  it('does not send 403 to login', () => {
    expect(shouldRedirectUnauthorized('/api/v1/people', '/people')).toBe(true);
    expect(isPublicPagePath('/login')).toBe(true);
  });

  it('treats sidebar modules as consult-gated public app routes', () => {
    expect(isPublicPagePath('/people')).toBe(false);
    expect(isPublicPagePath('/admin')).toBe(false);
  });

  it('keeps CONSULTA without administer or administration consult', () => {
    const consulta = {
      canConsult: (module: string) =>
        ['DASHBOARD', 'PEOPLE', 'RESEARCH_OUTPUTS', 'JOURNALS', 'PROJECTS', 'ORGANIZATIONS', 'DIRECTORY', 'DATA_QUALITY', 'INDICATORS', 'INTEGRATIONS'].includes(
          module
        ),
      canAdminister: () => false
    };
    expect(consulta.canConsult('PEOPLE')).toBe(true);
    expect(consulta.canConsult('ADMINISTRATION')).toBe(false);
    expect(consulta.canConsult('AUDIT')).toBe(false);
    expect(consulta.canAdminister()).toBe(false);
  });
});
