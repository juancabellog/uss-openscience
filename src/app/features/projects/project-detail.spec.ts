import { PROJECT_DETAIL_SECTIONS } from './project-detail.component';

describe('Project detail section navigation', () => {
  it('uses the shared in-page section nav rather than hash links to root', () => {
    expect(PROJECT_DETAIL_SECTIONS.map((item) => item.id)).toEqual([
      'identidad',
      'periodo',
      'clasificacion',
      'participantes',
      'organizaciones',
      'auditoria'
    ]);
  });
});
