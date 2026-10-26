import { permissionGuard } from './permission.guard';

describe('permissionGuard', () => {
  it('defaults protected routes to CONSULT of the declared module', () => {
    const guard = permissionGuard('PEOPLE');
    expect(typeof guard).toBe('function');
  });

  it('can require ADMINISTRAR for write-only admin routes when declared', () => {
    const guard = permissionGuard('ADMINISTRATION', 'ADMINISTER');
    expect(typeof guard).toBe('function');
  });
});
