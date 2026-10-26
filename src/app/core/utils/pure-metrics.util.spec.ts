import {
  citeScoreCellTitle,
  isMetricsSyncEntity,
  metricFetchKind,
  metricFetchMessageKey,
  numericObservation,
  observationDisplayValue,
  personMetricMatrix
} from './pure-metrics.util';
import { MetricObservation, PersonMetrics } from '../models/openscience.models';

function observation(partial: Partial<MetricObservation>): MetricObservation {
  return {
    collectionId: 'hIndex',
    sourceUri: null,
    sourceTermEs: null,
    sourceTermEn: null,
    categoryUri: null,
    categoryTermEs: null,
    categoryTermEn: null,
    metricYear: null,
    metricId: null,
    valueKind: null,
    integerValue: null,
    decimalValue: null,
    stringValue: null,
    lastSyncedAt: null,
    ...partial
  };
}

describe('PURE metrics display helpers', () => {
  it('distinguishes never, failed, empty and ready fetch states', () => {
    expect(metricFetchKind(null)).toBe('never');
    expect(metricFetchKind({ lastOutcome: 'NEVER', lastHttpStatus: null, lastErrorCode: null, lastErrorMessage: null, observationCount: 0, lastSyncedAt: null, lastSuccessAt: null, collectionId: 'sciVal' })).toBe('never');
    expect(metricFetchKind({ lastOutcome: 'FAILED', lastHttpStatus: 429, lastErrorCode: 'HTTP_429', lastErrorMessage: 'rate', observationCount: 0, lastSyncedAt: '2026-10-19', lastSuccessAt: null, collectionId: 'sciVal' })).toBe('failed');
    expect(metricFetchKind({ lastOutcome: 'INVALID', lastHttpStatus: 200, lastErrorCode: 'PARSE', lastErrorMessage: 'bad', observationCount: 0, lastSyncedAt: '2026-10-19', lastSuccessAt: null, collectionId: 'sciVal' })).toBe('failed');
    expect(metricFetchKind({ lastOutcome: 'EMPTY', lastHttpStatus: 200, lastErrorCode: null, lastErrorMessage: null, observationCount: 0, lastSyncedAt: '2026-10-19', lastSuccessAt: '2026-10-19', collectionId: 'sciVal' })).toBe('empty');
    expect(metricFetchKind({ lastOutcome: 'SUCCESS', lastHttpStatus: 200, lastErrorCode: null, lastErrorMessage: null, observationCount: 0, lastSyncedAt: '2026-10-19', lastSuccessAt: '2026-10-19', collectionId: 'sciVal' })).toBe('empty');
    expect(metricFetchKind({ lastOutcome: 'SUCCESS', lastHttpStatus: 200, lastErrorCode: null, lastErrorMessage: null, observationCount: 2, lastSyncedAt: '2026-10-19', lastSuccessAt: '2026-10-19', collectionId: 'sciVal' })).toBe('ready');
  });

  it('does not treat 0 as missing', () => {
    const zero = observation({ metricId: 'hindex', integerValue: 0, valueKind: 'INTEGER' });
    expect(observationDisplayValue(zero)).toBe('0');
    expect(numericObservation(zero)).toBe(0);
    expect(observationDisplayValue(observation({ metricId: 'hindex' }))).toBeNull();
  });

  it('preserves decimal FWCI and integer CiteScore-like values', () => {
    expect(observationDisplayValue(observation({ metricId: 'fwci', decimalValue: 0.57, valueKind: 'DECIMAL' }))).toBe('0.57');
    expect(observationDisplayValue(observation({ metricId: 'rating_citescore', decimalValue: 4.1, valueKind: 'DECIMAL' }))).toBe('4.1');
  });

  it('builds the person source × period matrix without collapsing sources', () => {
    const metrics: PersonMetrics = {
      personId: 1,
      uuid: 'p',
      hIndex: { collectionId: 'hIndex', lastOutcome: 'SUCCESS', lastHttpStatus: 200, lastErrorCode: null, lastErrorMessage: null, observationCount: 6, lastSyncedAt: '2026-10-19', lastSuccessAt: '2026-10-19' },
      scopusAllYears: null,
      observations: [
        observation({ sourceTermEn: 'Scopus', sourceTermEs: 'Scopus', categoryTermEn: 'All years', categoryTermEs: 'Todos los años', metricId: 'hindex', integerValue: 12 }),
        observation({ sourceTermEn: 'Scopus', sourceTermEs: 'Scopus', categoryTermEn: 'All years', categoryTermEs: 'Todos los años', metricId: 'totalcitations', integerValue: 450 }),
        observation({ sourceTermEn: 'Scopus', sourceTermEs: 'Scopus', categoryTermEn: '10 years', categoryTermEs: '10 años', metricId: 'hindex', integerValue: 10 }),
        observation({ sourceTermEn: 'PlumX', sourceTermEs: 'PlumX', categoryTermEn: 'All years', categoryTermEs: 'Todos los años', metricId: 'hindex', integerValue: 8 }),
        observation({ sourceTermEn: 'SciVal', sourceTermEs: 'SciVal', categoryTermEn: 'All years', categoryTermEs: 'Todos los años', metricId: 'hindex', integerValue: 9 })
      ]
    };
    const rows = personMetricMatrix(metrics);
    expect(rows.length).toBe(4);
    const scopusAll = rows.find((row) => row.sourceTermEn === 'Scopus' && row.categoryTermEn === 'All years');
    expect(scopusAll?.hindex).toBe(12);
    expect(scopusAll?.citations).toBe(450);
    expect(rows.some((row) => row.sourceTermEn === 'PlumX' && row.hindex === 8)).toBe(true);
    expect(rows.some((row) => row.sourceTermEn === 'SciVal' && row.hindex === 9)).toBe(true);
  });

  it('keeps CiteScore year in the compact cell title and identifies metrics sync entities', () => {
    expect(citeScoreCellTitle(3.5, 2025)).toBe('3.5 · 2025');
    expect(citeScoreCellTitle(null, 2025)).toBe('');
    expect(isMetricsSyncEntity('JOURNAL_METRICS')).toBe(true);
    expect(isMetricsSyncEntity('JOURNALS')).toBe(false);
    expect(metricFetchMessageKey('failed')).toBe('metrics.fetchFailed');
    expect(metricFetchMessageKey('empty')).toBe('metrics.fetchEmpty');
  });
});
