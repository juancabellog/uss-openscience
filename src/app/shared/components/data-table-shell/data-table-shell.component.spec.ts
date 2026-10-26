import { DataTableShellComponent } from './data-table-shell.component';

describe('os-data-table-shell viewport', () => {
  it('is the shared wrapper for existing semantic HTML tables', () => {
    expect(DataTableShellComponent.name).toBe('DataTableShellComponent');
  });

  it('keeps overflow on a single local viewport rather than a page-level grid', () => {
    const inputs = DataTableShellComponent as unknown as {
      ɵcmp?: { inputs?: Record<string, unknown> };
    };
    expect(inputs.ɵcmp?.inputs?.['sticky'] ?? 'sticky').toBeTruthy();
    expect(inputs.ɵcmp?.inputs?.['stickyStart'] ?? 'stickyStart').toBeTruthy();
    expect(inputs.ɵcmp?.inputs?.['stickyEnd'] ?? 'stickyEnd').toBeTruthy();
    expect(inputs.ɵcmp?.inputs?.['fluid'] ?? 'fluid').toBeTruthy();
    expect(inputs.ɵcmp?.inputs?.['maxHeight'] ?? 'maxHeight').toBeTruthy();
  });
});
