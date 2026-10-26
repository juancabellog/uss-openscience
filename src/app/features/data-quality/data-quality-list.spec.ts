import {
  CROSSREF_ORCID_PREVIEW,
  crossrefOrcidSuggestedText,
  dataQualityRulesForSource,
  parseCrossrefOrcidEvidence,
  previewCrossrefOrcids
} from './data-quality-list.component';

describe('CROSSREF-007 finding detail helpers', () => {
  it('shows the short suggested_value instead of a dump', () => {
    const summary = 'ORCID informado por Crossref y ausente institucionalmente.';
    const suggested =
      'Crossref informa 3.013 ORCID de autores sin equivalencia institucional en este registro.';
    expect(crossrefOrcidSuggestedText(suggested, summary)).toBe(suggested);
    expect(crossrefOrcidSuggestedText(','.repeat(300), summary)).toBe(summary);
  });

  it('parses evidence_json without rendering thousands of rows at once', () => {
    const orcids = Array.from({ length: 80 }, (_, index) => `0000-0002-0000-${String(index).padStart(4, '0')}`);
    const evidence = parseCrossrefOrcidEvidence(
      'CROSSREF-007',
      JSON.stringify({
        source: 'CROSSREF',
        doi: '10.1000/mega',
        status: 'FOUND',
        contextKey: 'ORCID_EXTERNAL|99',
        externalOrcidCount: 80,
        externalOrcids: orcids
      })
    );
    expect(evidence?.externalOrcidCount).toBe(80);
    expect(evidence?.doi).toBe('10.1000/mega');
    expect(previewCrossrefOrcids(evidence!.externalOrcids, CROSSREF_ORCID_PREVIEW).length).toBe(50);
    expect(previewCrossrefOrcids(evidence!.externalOrcids, 100).length).toBe(80);
  });

  it('filters rule dropdown by source_family, not rule_code text', () => {
    const counts = [
      { code: 'CROSSREF-007', name: 'ORCID Crossref', sourceFamily: 'CROSSREF', count: 4 },
      { code: 'PURE-002', name: 'Título', sourceFamily: 'PURE', count: 9 },
      { code: 'EXT-01', name: 'Título externo', sourceFamily: 'EXTERNAL', count: 2 }
    ];
    const rules = counts.map((item) => ({ ...item, sourceFamily: item.sourceFamily }) as never);
    expect(dataQualityRulesForSource(counts, rules, 'CROSSREF').map((item) => item.code)).toEqual(['CROSSREF-007']);
    expect(dataQualityRulesForSource(counts, rules, 'UNPAYWALL').map((item) => item.code)).toEqual([
      'CROSSREF-007',
      'PURE-002',
      'EXT-01'
    ]);
    expect(dataQualityRulesForSource(counts.map((item) => ({ ...item, count: 0 })), rules, 'UNPAYWALL')).toEqual([]);
  });

  it('ignores evidence from other rules', () => {
    expect(
      parseCrossrefOrcidEvidence(
        'CROSSREF-008',
        JSON.stringify({ externalOrcids: ['0000-0002-1825-0097'], externalOrcidCount: 1 })
      )
    ).toBeNull();
  });
});
