import { RESEARCH_OUTPUT_DETAIL_SECTIONS } from './research-output-detail.component';

describe('Research output detail section navigation', () => {
  it('keeps every in-page section on the current publication route', () => {
    expect(RESEARCH_OUTPUT_DETAIL_SECTIONS.map((item) => item.id)).toEqual([
      'general',
      'metricas',
      'identificadores',
      'publicacion',
      'contributors',
      'versiones',
      'keywords',
      'links'
    ]);
  });
});
