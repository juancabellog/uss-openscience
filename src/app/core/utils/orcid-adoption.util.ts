export type OrcidFirstSeenSource = 'BASELINE' | 'OBSERVED';

export interface OrcidAdoptionMonthView {
  month: string;
  totalPersons: number | null;
  personsWithOrcid: number | null;
  percentage: number | null;
  complete: boolean;
}

export function orcidFirstSeenLabelKey(source: string | null | undefined): string {
  return source === 'BASELINE' ? 'people.orcidPresentAtTrackingStart' : 'people.orcidObservedSince';
}

export function orcidFirstSeenIsPureAddDate(): boolean {
  return false;
}

export function latestCompleteOrcidMonth(
  months: OrcidAdoptionMonthView[] | null | undefined
): OrcidAdoptionMonthView | null {
  if (!months?.length) {
    return null;
  }
  for (let i = months.length - 1; i >= 0; i--) {
    const month = months[i];
    if (month.complete && month.percentage != null) {
      return month;
    }
  }
  return null;
}

export function orcidAdoptionLinePoints(
  months: OrcidAdoptionMonthView[] | null | undefined,
  width = 100,
  height = 40,
  pad = 4
): string {
  const complete = (months ?? []).filter((month) => month.complete && month.percentage != null);
  if (complete.length === 0) {
    return '';
  }
  const innerW = width - pad * 2;
  const innerH = height - pad * 2;
  return complete
    .map((month, index) => {
      const x = complete.length === 1 ? pad + innerW / 2 : pad + (index * innerW) / (complete.length - 1);
      const y = pad + innerH - ((month.percentage ?? 0) / 100) * innerH;
      return `${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(' ');
}
