import { JOURNAL_DETAIL_SECTIONS } from './journal-detail.component';

describe('Journal detail section navigation', () => {
  it('uses the shared in-page section nav rather than hash links to root', () => {
    expect(JOURNAL_DETAIL_SECTIONS.map((item) => item.id)).toEqual([
      'identidad',
      'titulos',
      'issns',
      'identificadores',
      'editorial',
      'asjc',
      'metricas',
      'auditoria'
    ]);
  });
});
