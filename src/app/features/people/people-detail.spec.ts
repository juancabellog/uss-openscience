import { PEOPLE_DETAIL_SECTIONS } from './people-detail.component';

describe('Person detail section navigation', () => {
  it('keeps every in-page section on the current person route', () => {
    expect(PEOPLE_DETAIL_SECTIONS.map((item) => item.id)).toEqual([
      'resumen',
      'identidad-institucional',
      'identidad',
      'metricas',
      'orcid-works'
    ]);
  });
});
