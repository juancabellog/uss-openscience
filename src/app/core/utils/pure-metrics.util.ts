import { MetricFetchState, MetricObservation, PersonMetrics } from '../models/openscience.models';

export type MetricFetchKind = 'never' | 'failed' | 'empty' | 'ready';

export function metricFetchKind(state: MetricFetchState | null | undefined): MetricFetchKind {
  if (!state?.lastOutcome) {
    return 'never';
  }
  const outcome = state.lastOutcome.trim().toUpperCase();
  if (outcome === 'NEVER') {
    return 'never';
  }
  if (outcome === 'FAILED' || outcome === 'INVALID') {
    return 'failed';
  }
  if (outcome === 'EMPTY') {
    return 'empty';
  }
  if (outcome === 'SUCCESS' && (state.observationCount ?? 0) === 0) {
    return 'empty';
  }
  if (outcome === 'SUCCESS') {
    return 'ready';
  }
  return 'never';
}

export function metricFetchMessageKey(kind: MetricFetchKind): string {
  switch (kind) {
    case 'failed':
      return 'metrics.fetchFailed';
    case 'empty':
      return 'metrics.fetchEmpty';
    case 'never':
      return 'metrics.fetchNever';
    default:
      return 'metrics.fetchReady';
  }
}

export function observationDisplayValue(
  obs: Pick<MetricObservation, 'integerValue' | 'decimalValue' | 'stringValue'>
): string | null {
  if (obs.integerValue != null) {
    return String(obs.integerValue);
  }
  if (obs.decimalValue != null) {
    return String(obs.decimalValue);
  }
  if (obs.stringValue != null && obs.stringValue !== '') {
    return obs.stringValue;
  }
  return null;
}

export function numericObservation(obs: MetricObservation): number | null {
  if (obs.integerValue != null) {
    return obs.integerValue;
  }
  if (obs.decimalValue != null) {
    return obs.decimalValue;
  }
  return null;
}

export interface PersonMetricMatrixRow {
  sourceUri: string | null;
  sourceTermEs: string | null;
  sourceTermEn: string | null;
  categoryUri: string | null;
  categoryTermEs: string | null;
  categoryTermEn: string | null;
  hindex: number | null;
  citations: number | null;
  lastSyncedAt: string | null;
}

export function personMetricMatrix(metrics: PersonMetrics | null | undefined): PersonMetricMatrixRow[] {
  const rows = new Map<string, PersonMetricMatrixRow>();
  for (const obs of metrics?.observations ?? []) {
    const key = [obs.sourceUri ?? '', obs.categoryUri ?? '', obs.sourceTermEn ?? '', obs.categoryTermEn ?? ''].join('|');
    const current = rows.get(key) ?? {
      sourceUri: obs.sourceUri,
      sourceTermEs: obs.sourceTermEs,
      sourceTermEn: obs.sourceTermEn,
      categoryUri: obs.categoryUri,
      categoryTermEs: obs.categoryTermEs,
      categoryTermEn: obs.categoryTermEn,
      hindex: null,
      citations: null,
      lastSyncedAt: obs.lastSyncedAt
    };
    const metricId = (obs.metricId ?? '').toLowerCase();
    if (metricId === 'hindex') {
      current.hindex = numericObservation(obs);
    } else if (metricId === 'totalcitations') {
      current.citations = numericObservation(obs);
    }
    if (obs.lastSyncedAt && (!current.lastSyncedAt || obs.lastSyncedAt > current.lastSyncedAt)) {
      current.lastSyncedAt = obs.lastSyncedAt;
    }
    rows.set(key, current);
  }
  return [...rows.values()];
}

export function isMetricsSyncEntity(entity?: string | null): boolean {
  const value = (entity ?? '').trim().toUpperCase();
  return value === 'JOURNAL_METRICS' || value === 'RESEARCH_OUTPUT_METRICS' || value === 'PERSON_METRICS';
}

export function citeScoreCellTitle(value: number | null, year: number | null): string {
  if (value == null) {
    return '';
  }
  return year != null ? `${value} · ${year}` : String(value);
}
