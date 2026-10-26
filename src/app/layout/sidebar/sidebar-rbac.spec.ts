import { AppModuleCode, AuthPermission } from '../../core/models/openscience.models';
import { visibleNavItems } from './nav-catalog';

const CONSULTA_PERMISSIONS: AuthPermission[] = [
  { module: 'DASHBOARD', permission: 'CONSULT' },
  { module: 'PEOPLE', permission: 'CONSULT' },
  { module: 'RESEARCH_OUTPUTS', permission: 'CONSULT' },
  { module: 'JOURNALS', permission: 'CONSULT' },
  { module: 'PROJECTS', permission: 'CONSULT' },
  { module: 'ORGANIZATIONS', permission: 'CONSULT' },
  { module: 'DIRECTORY', permission: 'CONSULT' },
  { module: 'DATA_QUALITY', permission: 'CONSULT' },
  { module: 'INDICATORS', permission: 'CONSULT' },
  { module: 'INTEGRATIONS', permission: 'CONSULT' }
];

function canConsult(module: AppModuleCode): boolean {
  return CONSULTA_PERMISSIONS.some((item) => item.module === module && item.permission === 'CONSULT');
}

function canAdminister(module: AppModuleCode): boolean {
  return CONSULTA_PERMISSIONS.some((item) => item.module === module && item.permission === 'ADMINISTER');
}

describe('CONSULTA sidebar and admin actions', () => {
  it('shows functional modules and hides Administración', () => {
    const paths = visibleNavItems(canConsult).map((item) => item.path);
    expect(paths).toContain('/dashboard');
    expect(paths).toContain('/people');
    expect(paths).toContain('/research-output');
    expect(paths).toContain('/journals');
    expect(paths).toContain('/projects');
    expect(paths).toContain('/organizations');
    expect(paths).toContain('/directory');
    expect(paths).toContain('/data-quality');
    expect(paths).toContain('/indicators');
    expect(paths).toContain('/integrations');
    expect(paths).not.toContain('/admin');
  });

  it('hides Auditoría', () => {
    const paths = visibleNavItems(canConsult).map((item) => item.path);
    expect(paths).not.toContain('/audit');
    expect(canConsult('AUDIT')).toBe(false);
    expect(canConsult('ADMINISTRATION')).toBe(false);
  });

  it('hides administer actions inside consultable modules', () => {
    expect(canAdminister('PEOPLE')).toBe(false);
    expect(canAdminister('RESEARCH_OUTPUTS')).toBe(false);
    expect(canAdminister('JOURNALS')).toBe(false);
    expect(canAdminister('DATA_QUALITY')).toBe(false);
    expect(canAdminister('INTEGRATIONS')).toBe(false);
    expect(canAdminister('ADMINISTRATION')).toBe(false);
  });
});
